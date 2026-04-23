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

// Node 抽象类：gtask 同步模块中所有可同步实体的抽象基类
package net.micode.notes.gtask.data;

import android.database.Cursor;   // 用于遍历本地 SQLite 数据库查询结果，在 getSyncAction(Cursor) 中用到
import org.json.JSONObject;       // 用于处理云端 Google Tasks API 返回的 JSON 数据，在序列化/反序列化方法中用到

/**
 * Node 抽象类：定义了所有参与同步的对象（如云端任务、本地便签、元数据）的共有属性和行为契约。
 * 它将 GID、名称、修改时间、删除标记等通用字段抽取出来，避免每个子类重复定义。
 * 子类只需要继承 Node，添加自身特有字段，并实现六个抽象方法即可参与同步流程。
 */
public abstract class Node {

    // ==================== 同步动作常量定义 ====================
    // 同步管理器（GTaskManager）在比对本地和云端数据后，会为每个节点分配一个动作代号，
    // 然后根据代号执行相应的上传、下载或删除操作。

    public static final int SYNC_ACTION_NONE = 0;            // 无需执行任何同步操作
    public static final int SYNC_ACTION_ADD_REMOTE = 1;      // 本地有、云端无 → 上传到云端
    public static final int SYNC_ACTION_ADD_LOCAL = 2;       // 云端有、本地无 → 下载到本地
    public static final int SYNC_ACTION_DEL_REMOTE = 3;      // 本地已删除、云端还有 → 删除云端记录
    public static final int SYNC_ACTION_DEL_LOCAL = 4;       // 云端已删除、本地还有 → 删除本地记录
    public static final int SYNC_ACTION_UPDATE_REMOTE = 5;   // 本地内容更新 → 上传覆盖云端
    public static final int SYNC_ACTION_UPDATE_LOCAL = 6;    // 云端内容更新 → 下载覆盖本地
    public static final int SYNC_ACTION_UPDATE_CONFLICT = 7; // 本地和云端同时修改 → 发生冲突
    public static final int SYNC_ACTION_ERROR = 8;           // 同步过程中出现错误

    // ==================== 共有成员变量 ====================
    // 所有同步节点都具备以下四个基础属性

    private String mGid;            // Google Task ID，云端资源的唯一标识符。若为本地新增节点，该值可能为 null
    private String mName;           // 节点名称。对于普通任务是标题，对于 MetaData 则为固定字符串 "Meta Data"
    private long mLastModified;     // 最后修改时间的时间戳（毫秒），用于判断本地与云端谁更新
    private boolean mDeleted;       // 标记该节点是否已被删除（逻辑删除）。同步时据此决定是否在云端执行删除

    // ==================== 构造方法 ====================
    /**
     * 无参构造方法：初始化一个“空白”节点，所有属性均设为默认值。
     * 由于 Node 是抽象类，该构造方法只能在子类（如 Task）的构造方法中通过 super() 调用，
     * 用于完成父类字段的初始化。
     */
    public Node() {
        mGid = null;
        mName = "";
        mLastModified = 0;
        mDeleted = false;
    }

    // ==================== 抽象方法（由子类实现） ====================
    // 以下六个方法定义了同步过程中必须的行为，具体实现因节点类型不同而不同。

    /**
     * 将当前节点对象转换为“创建”操作所需的 JSON 格式，用于向云端发送 POST 请求。
     * @param actionId 操作类型标识（用于区分是新增到云端还是新增到本地）
     * @return 符合 Google Tasks API 要求的 JSONObject
     */
    public abstract JSONObject getCreateAction(int actionId);

    /**
     * 将当前节点对象转换为“更新”操作所需的 JSON 格式，用于向云端发送 PUT 请求。
     * @param actionId 操作类型标识
     * @return 符合 Google Tasks API 要求的 JSONObject
     */
    public abstract JSONObject getUpdateAction(int actionId);

    /**
     * 用从云端拉取的 JSON 数据填充当前对象的属性。
     * @param js 云端返回的 JSON 对象
     */
    public abstract void setContentByRemoteJSON(JSONObject js);

    /**
     * 用从本地数据库读取的 JSON 数据填充当前对象的属性。
     * @param js 本地存储的 JSON 对象
     */
    public abstract void setContentByLocalJSON(JSONObject js);

    /**
     * 将当前对象的内容转换为可存入本地数据库的 JSON 格式。
     * @return 包含节点数据的 JSONObject
     */
    public abstract JSONObject getLocalJSONFromContent();

    /**
     * 根据本地数据库游标判断该节点需要执行哪种同步操作。
     * @param c 指向本地数据库记录的游标
     * @return 同步动作常量（如 SYNC_ACTION_ADD_REMOTE）
     */
    public abstract int getSyncAction(Cursor c);

    // ==================== Getter 和 Setter 方法 ====================
    // 提供对私有成员变量的安全访问（封装性）
    // 外部类（如 GTaskManager）通过这些方法读写属性，便于后期维护（如在 setter 中加入校验逻辑）

    public void setGid(String gid) {
        this.mGid = gid;
    }

    public void setName(String name) {
        this.mName = name;
    }

    public void setLastModified(long lastModified) {
        this.mLastModified = lastModified;
    }

    public void setDeleted(boolean deleted) {
        this.mDeleted = deleted;
    }

    public String getGid() {
        return this.mGid;
    }

    public String getName() {
        return this.mName;
    }

    public long getLastModified() {
        return this.mLastModified;
    }

    public boolean getDeleted() {
        return this.mDeleted;
    }
}