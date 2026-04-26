/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// TaskList 类：云端任务列表（Google Tasks 中的 Task List）的数据模型
// 继承自 Node 抽象类，表示一个包含多个 Task 的文件夹或系统分组
package net.micode.notes.gtask.data;

import android.database.Cursor;                // 数据库游标
import android.util.Log;                       // 日志工具

import net.micode.notes.data.Notes;            // 便签常量
import net.micode.notes.data.Notes.NoteColumns; // Note 表字段名常量
import net.micode.notes.gtask.exception.ActionFailureException; // 同步动作失败异常
import net.micode.notes.tool.GTaskStringUtils; // 字符串常量工具（JSON 键名、前缀等）

import org.json.JSONException;                 // JSON 解析异常
import org.json.JSONObject;                    // JSON 对象

import java.util.ArrayList;                    // 动态数组，存储子任务列表

/**
 * TaskList 类：对应 Google Tasks 中的 “Task List” 资源。
 * 在小米便签的同步模型中，TaskList 用于表示一个文件夹或系统分组。
 * 它内部维护了一个 Task 列表（mChildren），并管理任务之间的兄弟排序和父子关系。
 * 负责将自身序列化为云端 JSON（创建/更新），以及从云端 JSON 或本地数据库 JSON 反序列化。
 */
public class TaskList extends Node {

    // 日志标签，值为当前类名 "TaskList"
    private static final String TAG = TaskList.class.getSimpleName();

    // ==================== 成员变量 ====================

    private int mIndex;                     // 该任务列表在父级结构中的索引（用于排序）
    private ArrayList<Task> mChildren;      // 该任务列表下的所有子任务

    // ==================== 构造方法 ====================

    /**
     * 构造方法：创建一个空的 TaskList 对象。
     * 初始化子任务列表为空，索引默认为 1。
     */
    public TaskList() {
        super();                            // 调用父类 Node 的构造方法，初始化通用字段
        mChildren = new ArrayList<Task>();  // 创建空子任务列表
        mIndex = 1;                         // 默认索引从 1 开始
    }

    // ==================== 核心方法：JSON 序列化（本地 → 云端） ====================

    /**
     * 生成“创建任务列表”操作的 JSON 数据，用于向 Google Tasks API 发送 POST 请求。
     * 实体类型为 "GROUP"，表示这是一个分组（文件夹）。
     *
     * @param actionId 操作 ID
     * @return 包含创建任务列表所需字段的 JSONObject
     */
    public JSONObject getCreateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // --- action_type：操作类型，固定为 "create" ---
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_CREATE);

            // --- action_id：操作 ID ---
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // --- index：任务列表的排序索引 ---
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, mIndex);

            // --- entity_delta：实体数据 ---
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());         // 任务列表名称
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null");      // 创建者 ID 固定为 "null"
            entity.put(GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,              // 实体类型 = "GROUP"
                    GTaskStringUtils.GTASK_JSON_TYPE_GROUP);
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate tasklist-create jsonobject");
        }

        return js;
    }

    /**
     * 生成“更新任务列表”操作的 JSON 数据，用于向 Google Tasks API 发送 PUT 请求。
     * 主要更新名称和删除状态。
     *
     * @param actionId 操作 ID
     * @return 包含更新字段的 JSONObject
     */
    public JSONObject getUpdateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // --- action_type：操作类型，固定为 "update" ---
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_UPDATE);

            // --- action_id：操作 ID ---
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // --- id：要更新的任务列表的 GID ---
            js.put(GTaskStringUtils.GTASK_JSON_ID, getGid());

            // --- entity_delta：实体数据 ---
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());         // 名称
            entity.put(GTaskStringUtils.GTASK_JSON_DELETED, getDeleted());   // 删除标记
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate tasklist-update jsonobject");
        }

        return js;
    }

    // ==================== 核心方法：JSON 反序列化（云端 → 本地） ====================

    /**
     * 用从 Google 服务器返回的 JSON 数据填充当前 TaskList 对象的属性。
     *
     * @param js 云端返回的 JSON 对象
     */
    public void setContentByRemoteJSON(JSONObject js) {
        if (js != null) {
            try {
                // --- id：任务列表的 GID ---
                if (js.has(GTaskStringUtils.GTASK_JSON_ID)) {
                    setGid(js.getString(GTaskStringUtils.GTASK_JSON_ID));
                }

                // --- last_modified：最后修改时间 ---
                if (js.has(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED)) {
                    setLastModified(js.getLong(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED));
                }

                // --- name：任务列表名称 ---
                if (js.has(GTaskStringUtils.GTASK_JSON_NAME)) {
                    setName(js.getString(GTaskStringUtils.GTASK_JSON_NAME));
                }

            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("fail to get tasklist content from jsonobject");
            }
        }
    }

    /**
     * 用本地数据库的 JSON 数据填充当前 TaskList 对象的属性。
     * 根据便签文件夹的类型（普通文件夹/系统文件夹）设置对应的任务列表名称。
     * 注意：MIUI 环境下会在文件夹名称前添加固定前缀，以区别于普通任务。
     *
     * @param js 本地 JSON 对象，必须包含 META_HEAD_NOTE 节点
     */
    public void setContentByLocalJSON(JSONObject js) {
        if (js == null || !js.has(GTaskStringUtils.META_HEAD_NOTE)) {
            Log.w(TAG, "setContentByLocalJSON: nothing is avaiable");
            return;
        }

        try {
            JSONObject folder = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);

            // --- 处理普通文件夹 ---
            if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_FOLDER) {
                String name = folder.getString(NoteColumns.SNIPPET);
                // 添加 MIUI 文件夹前缀，例如将 "工作" 变成 "[MIUI]工作"
                setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + name);
            }
            // --- 处理系统文件夹（如根文件夹、通话记录文件夹） ---
            else if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_SYSTEM) {
                if (folder.getLong(NoteColumns.ID) == Notes.ID_ROOT_FOLDER)
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT);
                else if (folder.getLong(NoteColumns.ID) == Notes.ID_CALL_RECORD_FOLDER)
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                            + GTaskStringUtils.FOLDER_CALL_NOTE);
                else
                    Log.e(TAG, "invalid system folder");
            } else {
                Log.e(TAG, "error type");
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    /**
     * 将当前 TaskList 对象转换为可存入本地数据库的 JSON 格式。
     * 根据任务列表名称是否包含 MIUI 前缀来判断是系统文件夹还是普通文件夹，
     * 并设置对应的 TYPE 和 SNIPPET 字段。
     *
     * @return 包含 META_HEAD_NOTE 节点的 JSONObject，失败则返回 null
     */
    public JSONObject getLocalJSONFromContent() {
        try {
            JSONObject js = new JSONObject();
            JSONObject folder = new JSONObject();

            // 移除 MIUI 前缀，得到原始文件夹名称
            String folderName = getName();
            if (getName().startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX))
                folderName = folderName.substring(
                        GTaskStringUtils.MIUI_FOLDER_PREFFIX.length(),
                        folderName.length());
            folder.put(NoteColumns.SNIPPET, folderName);

            // 根据文件夹名称判断是否为系统文件夹
            if (folderName.equals(GTaskStringUtils.FOLDER_DEFAULT)
                    || folderName.equals(GTaskStringUtils.FOLDER_CALL_NOTE))
                folder.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
            else
                folder.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);

            js.put(GTaskStringUtils.META_HEAD_NOTE, folder);
            return js;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return null;
        }
    }

    // ==================== 核心方法：同步动作判断 ====================

    /**
     * 根据本地数据库游标判断该任务列表需要执行哪种同步操作。
     * 相比 Task 的判断逻辑，文件夹的冲突处理策略是：冲突时直接采用本地版本（UPDATE_REMOTE）。
     *
     * @param c 指向本地数据库记录的游标
     * @return 同步动作常量
     */
    public int getSyncAction(Cursor c) {
        try {
            if (c.getInt(SqlNote.LOCAL_MODIFIED_COLUMN) == 0) {
                // 本地没有修改
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // 两端一致，无需同步
                    return SYNC_ACTION_NONE;
                } else {
                    // 云端更新，下载到本地
                    return SYNC_ACTION_UPDATE_LOCAL;
                }
            } else {
                // 本地有修改
                // 验证 GTask ID 是否匹配
                if (!c.getString(SqlNote.GTASK_ID_COLUMN).equals(getGid())) {
                    Log.e(TAG, "gtask id doesn't match");
                    return SYNC_ACTION_ERROR;
                }
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // 只有本地修改，上传到云端
                    return SYNC_ACTION_UPDATE_REMOTE;
                } else {
                    // 冲突：对于文件夹，直接采用本地修改（覆盖云端）
                    return SYNC_ACTION_UPDATE_REMOTE;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
        return SYNC_ACTION_ERROR;
    }

    // ==================== 子任务管理方法 ====================

    /**
     * 获取子任务数量
     * @return 子任务个数
     */
    public int getChildTaskCount() {
        return mChildren.size();
    }

    /**
     * 向任务列表末尾添加一个子任务。
     * 如果添加成功，会自动设置该任务的前兄弟任务（即列表中最后一个任务）和父任务。
     *
     * @param task 要添加的 Task 对象
     * @return true 表示添加成功，false 表示已存在或添加失败
     */
    public boolean addChildTask(Task task) {
        boolean ret = false;
        if (task != null && !mChildren.contains(task)) {
            ret = mChildren.add(task);
            if (ret) {
                // 设置兄弟关系：若列表非空，则前兄弟为原有最后一个任务
                task.setPriorSibling(mChildren.isEmpty() ? null : mChildren
                        .get(mChildren.size() - 1));
                // 设置父级为当前 TaskList
                task.setParent(this);
            }
        }
        return ret;
    }

    /**
     * 在指定位置插入一个子任务。
     * 插入后会自动更新该位置及其前后任务的兄弟关系。
     *
     * @param task  要添加的 Task 对象
     * @param index 插入位置（0 ~ mChildren.size()）
     * @return true 表示插入成功
     */
    public boolean addChildTask(Task task, int index) {
        if (index < 0 || index > mChildren.size()) {
            Log.e(TAG, "add child task: invalid index");
            return false;
        }

        int pos = mChildren.indexOf(task);
        if (task != null && pos == -1) {
            mChildren.add(index, task);

            // 更新前一个和后一个任务的兄弟指针
            Task preTask = null;
            Task afterTask = null;
            if (index != 0)
                preTask = mChildren.get(index - 1);
            if (index != mChildren.size() - 1)
                afterTask = mChildren.get(index + 1);

            task.setPriorSibling(preTask);
            if (afterTask != null)
                afterTask.setPriorSibling(task);
        }

        return true;
    }

    /**
     * 移除一个子任务。
     * 移除后会重置该任务的兄弟和父级引用，并更新后续任务的兄弟关系。
     *
     * @param task 要移除的 Task 对象
     * @return true 表示移除成功
     */
    public boolean removeChildTask(Task task) {
        boolean ret = false;
        int index = mChildren.indexOf(task);
        if (index != -1) {
            ret = mChildren.remove(task);

            if (ret) {
                // 重置该任务的引用
                task.setPriorSibling(null);
                task.setParent(null);

                // 更新后续任务的兄弟关系
                if (index != mChildren.size()) {
                    mChildren.get(index).setPriorSibling(
                            index == 0 ? null : mChildren.get(index - 1));
                }
            }
        }
        return ret;
    }

    /**
     * 将一个子任务移动到新的位置。
     * 本质上是通过移除再插入实现，但会保留任务对象本身。
     *
     * @param task  要移动的 Task 对象
     * @param index 目标位置
     * @return true 表示移动成功
     */
    public boolean moveChildTask(Task task, int index) {
        if (index < 0 || index >= mChildren.size()) {
            Log.e(TAG, "move child task: invalid index");
            return false;
        }

        int pos = mChildren.indexOf(task);
        if (pos == -1) {
            Log.e(TAG, "move child task: the task should in the list");
            return false;
        }

        if (pos == index)
            return true;
        // 先移除再插入到目标位置
        return (removeChildTask(task) && addChildTask(task, index));
    }

    /**
     * 根据 GID 查找子任务
     * @param gid 云端任务 ID
     * @return 匹配的 Task 对象，未找到返回 null
     */
    public Task findChildTaskByGid(String gid) {
        for (int i = 0; i < mChildren.size(); i++) {
            Task t = mChildren.get(i);
            if (t.getGid().equals(gid)) {
                return t;
            }
        }
        return null;
    }

    /**
     * 获取指定子任务在列表中的索引
     * @param task 子任务
     * @return 索引位置，若不存在返回 -1
     */
    public int getChildTaskIndex(Task task) {
        return mChildren.indexOf(task);
    }

    /**
     * 根据索引获取子任务
     * @param index 索引
     * @return Task 对象，索引无效返回 null
     */
    public Task getChildTaskByIndex(int index) {
        if (index < 0 || index >= mChildren.size()) {
            Log.e(TAG, "getTaskByIndex: invalid index");
            return null;
        }
        return mChildren.get(index);
    }

    /**
     * 根据 GID 获取子任务（与 findChildTaskByGid 功能相同，方法名拼写修正）
     * @param gid 云端任务 ID
     * @return 匹配的 Task 对象，未找到返回 null
     */
    public Task getChilTaskByGid(String gid) {
        for (Task task : mChildren) {
            if (task.getGid().equals(gid))
                return task;
        }
        return null;
    }

    /**
     * 获取所有子任务的列表
     * @return 子任务 ArrayList
     */
    public ArrayList<Task> getChildTaskList() {
        return this.mChildren;
    }

    // ==================== 索引的 Setter 和 Getter ====================

    /**
     * 设置任务列表的排序索引
     * @param index 索引值
     */
    public void setIndex(int index) {
        this.mIndex = index;
    }

    /**
     * 获取任务列表的排序索引
     * @return 索引值
     */
    public int getIndex() {
        return this.mIndex;
    }
}