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

// Task 类：同步模块的核心数据模型，表示一个 Google Tasks 云端任务
// 它继承自 Node 抽象类，实现了 JSON 序列化/反序列化、同步动作判断等核心逻辑
package net.micode.notes.gtask.data;

import android.database.Cursor;                // 数据库游标，用于遍历本地查询结果
import android.text.TextUtils;                 // 字符串工具类，用于判断字符串是否为空或比较
import android.util.Log;                       // 日志工具

import net.micode.notes.data.Notes;            // 便签相关常量（类型定义等）
import net.micode.notes.data.Notes.DataColumns;   // Data 表字段名常量
import net.micode.notes.data.Notes.DataConstants; // Data 表常量值
import net.micode.notes.data.Notes.NoteColumns;   // Note 表字段名常量
import net.micode.notes.gtask.exception.ActionFailureException; // 同步操作失败异常
import net.micode.notes.tool.GTaskStringUtils;    // 字符串常量工具类（JSON 键名等）

import org.json.JSONArray;                     // JSON 数组，用于存储多条 Data 记录
import org.json.JSONException;                 // JSON 解析异常
import org.json.JSONObject;                    // JSON 对象，用于与 Google Tasks API 交互

/**
 * Task 类：继承自 Node，是 Google Tasks 同步模块的核心数据载体。
 * 一个 Task 对象对应云端的一条任务（Task Resource）。
 * 它负责：
 *   1. 将本地便签转换为云端 JSON 格式（getCreateAction / getUpdateAction）
 *   2. 将云端 JSON 数据解析到本地对象（setContentByRemoteJSON）
 *   3. 判断同步操作类型（getSyncAction）
 * 同时，它还维护了任务之间的兄弟关系（mPriorSibling）和父子关系（mParent）。
 */
public class Task extends Node {

    // 日志标签，值为当前类名 "Task"
    private static final String TAG = Task.class.getSimpleName();

    // ==================== 成员变量 ====================

    private boolean mCompleted;      // 任务是否已完成（对应 Google Tasks 的 "completed" 字段）
    private String mNotes;           // 任务的备注内容（对应 Google Tasks 的 "notes" 字段）
    private JSONObject mMetaInfo;    // 与该任务关联的本地便签元数据（包含 Note 和 Data 信息）
    private Task mPriorSibling;      // 该任务的前一个兄弟任务（用于云端排序）
    private TaskList mParent;        // 该任务所属的父任务列表

    // ==================== 构造方法 ====================

    /**
     * 无参构造方法：创建一个空任务对象，所有属性设为默认值。
     * 先调用父类 Node 的构造方法初始化通用字段（GID、名称、时间戳等）。
     */
    public Task() {
        super();                    // 调用父类 Node 的构造方法
        mCompleted = false;         // 默认未完成
        mNotes = null;              // 默认无备注
        mPriorSibling = null;       // 默认无前兄弟
        mParent = null;             // 默认无父任务列表
        mMetaInfo = null;           // 默认无元数据
    }

    // ==================== 核心方法：JSON 序列化（本地 → 云端） ====================

    /**
     * 生成“创建任务”操作的 JSON 数据，用于向 Google Tasks API 发送 POST 请求。
     * 该方法将当前 Task 对象转换为符合 Google Tasks API 格式的 JSONObject。
     *
     * @param actionId 操作 ID（用于标识此次操作）
     * @return 包含创建任务所需全部字段的 JSONObject
     */
    public JSONObject getCreateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // --- action_type：操作类型，固定为 "create" ---
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_CREATE);

            // --- action_id：操作 ID ---
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // --- index：任务在列表中的位置索引 ---
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, mParent.getChildTaskIndex(this));

            // --- entity_delta：实体数据（任务的详细属性）---
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());        // 任务名称
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null");     // 创建者 ID（固定为 "null"）
            entity.put(GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,             // 实体类型 = "TASK"
                    GTaskStringUtils.GTASK_JSON_TYPE_TASK);
            if (getNotes() != null) {
                entity.put(GTaskStringUtils.GTASK_JSON_NOTES, getNotes());  // 备注（如果存在）
            }
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

            // --- parent_id：父任务列表的 GID ---
            js.put(GTaskStringUtils.GTASK_JSON_PARENT_ID, mParent.getGid());

            // --- dest_parent_type：目标父级类型，固定为 "GROUP" ---
            js.put(GTaskStringUtils.GTASK_JSON_DEST_PARENT_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_GROUP);

            // --- list_id：所在列表的 GID ---
            js.put(GTaskStringUtils.GTASK_JSON_LIST_ID, mParent.getGid());

            // --- prior_sibling_id：前一个兄弟任务的 GID（用于排序）---
            if (mPriorSibling != null) {
                js.put(GTaskStringUtils.GTASK_JSON_PRIOR_SIBLING_ID, mPriorSibling.getGid());
            }

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate task-create jsonobject");
        }

        return js;
    }

    /**
     * 生成“更新任务”操作的 JSON 数据，用于向 Google Tasks API 发送 PUT 请求。
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

            // --- id：要更新的任务的 GID ---
            js.put(GTaskStringUtils.GTASK_JSON_ID, getGid());

            // --- entity_delta：实体数据（仅包含需要更新的字段）---
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());        // 任务名称
            if (getNotes() != null) {
                entity.put(GTaskStringUtils.GTASK_JSON_NOTES, getNotes());  // 备注（如果存在）
            }
            entity.put(GTaskStringUtils.GTASK_JSON_DELETED, getDeleted());  // 删除标记
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate task-update jsonobject");
        }

        return js;
    }

    // ==================== 核心方法：JSON 反序列化（云端 → 本地） ====================

    /**
     * 用从 Google 服务器返回的 JSON 数据填充当前 Task 对象的属性。
     * 该方法在同步拉取云端数据时被调用。
     *
     * @param js 云端返回的 JSON 对象
     */
    public void setContentByRemoteJSON(JSONObject js) {
        if (js != null) {
            try {
                // --- id：任务的 GID ---
                if (js.has(GTaskStringUtils.GTASK_JSON_ID)) {
                    setGid(js.getString(GTaskStringUtils.GTASK_JSON_ID));
                }

                // --- last_modified：最后修改时间 ---
                if (js.has(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED)) {
                    setLastModified(js.getLong(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED));
                }

                // --- name：任务名称 ---
                if (js.has(GTaskStringUtils.GTASK_JSON_NAME)) {
                    setName(js.getString(GTaskStringUtils.GTASK_JSON_NAME));
                }

                // --- notes：任务备注 ---
                if (js.has(GTaskStringUtils.GTASK_JSON_NOTES)) {
                    setNotes(js.getString(GTaskStringUtils.GTASK_JSON_NOTES));
                }

                // --- deleted：是否已删除 ---
                if (js.has(GTaskStringUtils.GTASK_JSON_DELETED)) {
                    setDeleted(js.getBoolean(GTaskStringUtils.GTASK_JSON_DELETED));
                }

                // --- completed：是否已完成 ---
                if (js.has(GTaskStringUtils.GTASK_JSON_COMPLETED)) {
                    setCompleted(js.getBoolean(GTaskStringUtils.GTASK_JSON_COMPLETED));
                }
            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("fail to get task content from jsonobject");
            }
        }
    }

    /**
     * 用本地数据库的 JSON 数据填充当前 Task 对象的属性。
     * 该方法在将本地便签转换为 Task 对象时调用，会从 Data 列表中提取正文作为任务名称。
     *
     * @param js 本地存储的 JSON 对象，包含 Note 信息和 Data 数组
     */
    public void setContentByLocalJSON(JSONObject js) {
        // 安全检查：JSON 必须包含 META_HEAD_NOTE 和 META_HEAD_DATA
        if (js == null || !js.has(GTaskStringUtils.META_HEAD_NOTE)
                || !js.has(GTaskStringUtils.META_HEAD_DATA)) {
            Log.w(TAG, "setContentByLocalJSON: nothing is avaiable");
            return;  // 不抛异常，只是警告后返回
        }

        try {
            JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
            JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);

            // 只有类型为 TYPE_NOTE（便签）才处理
            if (note.getInt(NoteColumns.TYPE) != Notes.TYPE_NOTE) {
                Log.e(TAG, "invalid type");
                return;
            }

            // 遍历 Data 列表，找到 MIME 类型为 NOTE 的那条（即便签正文），将其内容作为任务名称
            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject data = dataArray.getJSONObject(i);
                if (TextUtils.equals(data.getString(DataColumns.MIME_TYPE), DataConstants.NOTE)) {
                    setName(data.getString(DataColumns.CONTENT));
                    break;
                }
            }

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    /**
     * 将当前 Task 对象的内容转换为可存入本地数据库的 JSON 格式。
     * 该方法在同步下载时会用到，用于生成待插入本地数据库的 Data 记录。
     *
     * @return 包含 Note 信息和 Data 数组的 JSONObject，失败则返回 null
     */
    public JSONObject getLocalJSONFromContent() {
        String name = getName();
        try {
            if (mMetaInfo == null) {
                // --- 情况一：没有元数据，说明是从网页端新建的任务，需要全新构建 JSON ---
                if (name == null) {
                    Log.w(TAG, "the note seems to be an empty one");
                    return null;
                }

                JSONObject js = new JSONObject();
                JSONObject note = new JSONObject();       // Note 信息
                JSONArray dataArray = new JSONArray();    // Data 数组
                JSONObject data = new JSONObject();       // 一条 Data 记录
                data.put(DataColumns.CONTENT, name);       // 将任务名称作为正文内容
                dataArray.put(data);
                js.put(GTaskStringUtils.META_HEAD_DATA, dataArray);
                note.put(NoteColumns.TYPE, Notes.TYPE_NOTE);  // 类型 = 便签
                js.put(GTaskStringUtils.META_HEAD_NOTE, note);
                return js;

            } else {
                // --- 情况二：有元数据，说明是已同步的任务，只需更新名称字段 ---
                JSONObject note = mMetaInfo.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
                JSONArray dataArray = mMetaInfo.getJSONArray(GTaskStringUtils.META_HEAD_DATA);

                // 找到正文 Data 记录，更新其 CONTENT 字段为最新名称
                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject data = dataArray.getJSONObject(i);
                    if (TextUtils.equals(data.getString(DataColumns.MIME_TYPE), DataConstants.NOTE)) {
                        data.put(DataColumns.CONTENT, getName());
                        break;
                    }
                }

                note.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
                return mMetaInfo;
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return null;
        }
    }

    // ==================== 元数据管理 ====================

    /**
     * 从 MetaData 对象中解析并设置元数据信息。
     * MetaData 的 notes 字段中存储了一个 JSON 字符串，包含 Note 和 Data 的映射信息。
     *
     * @param metaData 元数据对象
     */
    public void setMetaInfo(MetaData metaData) {
        if (metaData != null && metaData.getNotes() != null) {
            try {
                mMetaInfo = new JSONObject(metaData.getNotes());
            } catch (JSONException e) {
                Log.w(TAG, e.toString());
                mMetaInfo = null;
            }
        }
    }

    // ==================== 核心方法：同步动作判断 ====================

    /**
     * 根据本地数据库游标判断该任务需要执行哪种同步操作。
     * 这是同步三方比对的核心逻辑：比较本地元数据与当前云端任务状态，决定是上传、下载还是冲突。
     *
     * @param c 指向本地数据库记录的游标（对应一条便签）
     * @return 同步动作常量（如 SYNC_ACTION_UPDATE_REMOTE）
     */
    public int getSyncAction(Cursor c) {
        try {
            // --- 1. 尝试从本地元数据中获取 Note 信息 ---
            JSONObject noteInfo = null;
            if (mMetaInfo != null && mMetaInfo.has(GTaskStringUtils.META_HEAD_NOTE)) {
                noteInfo = mMetaInfo.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
            }

            // --- 2. 元数据不存在 → 本地新创建的便签，上传到云端 ---
            if (noteInfo == null) {
                Log.w(TAG, "it seems that note meta has been deleted");
                return SYNC_ACTION_UPDATE_REMOTE;
            }

            // --- 3. 元数据中没有 ID → 本地记录异常，从云端下载 ---
            if (!noteInfo.has(NoteColumns.ID)) {
                Log.w(TAG, "remote note id seems to be deleted");
                return SYNC_ACTION_UPDATE_LOCAL;
            }

            // --- 4. 验证本地便签 ID 与元数据中的 ID 是否匹配 ---
            if (c.getLong(SqlNote.ID_COLUMN) != noteInfo.getLong(NoteColumns.ID)) {
                Log.w(TAG, "note id doesn't match");
                return SYNC_ACTION_ERROR;  // ID 不匹配，标记为错误
            }

            // --- 5. 核心比对逻辑 ---
            if (c.getInt(SqlNote.LOCAL_MODIFIED_COLUMN) == 0) {
                // 本地没有修改
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // 本地同步时间戳 == 云端最后修改时间 → 两端一致，无需同步
                    return SYNC_ACTION_NONE;
                } else {
                    // 云端更新 → 下载到本地
                    return SYNC_ACTION_UPDATE_LOCAL;
                }
            } else {
                // 本地有修改
                // 先验证 Google Tasks ID 是否匹配
                if (!c.getString(SqlNote.GTASK_ID_COLUMN).equals(getGid())) {
                    Log.e(TAG, "gtask id doesn't match");
                    return SYNC_ACTION_ERROR;
                }
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // 只有本地修改，云端未变 → 上传到云端
                    return SYNC_ACTION_UPDATE_REMOTE;
                } else {
                    // 本地和云端同时修改 → 冲突
                    return SYNC_ACTION_UPDATE_CONFLICT;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }

        return SYNC_ACTION_ERROR;
    }

    /**
     * 判断当前任务是否值得保存到本地数据库。
     * 与 MetaData 的 isWorthSaving 不同，Task 的判断条件更丰富。
     *
     * @return true 表示值得保存
     */
    public boolean isWorthSaving() {
        // 有元数据、有名称、有备注，三者满足其一即可
        return mMetaInfo != null
                || (getName() != null && getName().trim().length() > 0)
                || (getNotes() != null && getNotes().trim().length() > 0);
    }

    // ==================== Setter 和 Getter 方法 ====================

    public void setCompleted(boolean completed) {
        this.mCompleted = completed;
    }

    public void setNotes(String notes) {
        this.mNotes = notes;
    }

    /**
     * 设置前一个兄弟任务（用于维护云端任务列表中的排序）
     * @param priorSibling 前一个兄弟任务
     */
    public void setPriorSibling(Task priorSibling) {
        this.mPriorSibling = priorSibling;
    }

    /**
     * 设置父任务列表
     * @param parent 父 TaskList 对象
     */
    public void setParent(TaskList parent) {
        this.mParent = parent;
    }

    public boolean getCompleted() {
        return this.mCompleted;
    }

    public String getNotes() {
        return this.mNotes;
    }

    public Task getPriorSibling() {
        return this.mPriorSibling;
    }

    public TaskList getParent() {
        return this.mParent;
    }
}