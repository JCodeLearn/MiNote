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

// GTaskStringUtils：字符串常量工具类，集中管理 Google Tasks 同步过程中用到的所有 JSON 键名和固定字符串
package net.micode.notes.tool;

/**
 * GTaskStringUtils：同步模块的字符串常量定义类。
 * <p>
 * 该类集中定义了与 Google Tasks API 交互时用于构建和解析 JSON 的所有键名，
 * 以及 MIUI 文件夹前缀、系统文件夹名称、元数据标识符等本地固定字符串。
 * </p>
 * <p>
 * 使用常量的目的是避免在多处硬编码字符串，降低拼写错误风险，提升代码可维护性。
 * 所有属性均为 public final static，意味着在程序的任何地方都可以直接通过类名访问。
 * </p>
 */
public class GTaskStringUtils {

    // ==================== JSON Action 相关键名 ====================
    // 以下常量用于构建同步请求的 JSON 结构

    public final static String GTASK_JSON_ACTION_ID = "action_id";
    // 操作ID，用于标识一次同步请求中的某个具体操作

    public final static String GTASK_JSON_ACTION_LIST = "action_list";
    // 操作列表，包含一批需要执行的同步操作

    public final static String GTASK_JSON_ACTION_TYPE = "action_type";
    // 操作类型，具体取值为下方的 CREATE / GETALL / MOVE / UPDATE

    // ==================== Action Type 取值 ====================
    public final static String GTASK_JSON_ACTION_TYPE_CREATE = "create";
    // 操作类型：创建（POST 请求）

    public final static String GTASK_JSON_ACTION_TYPE_GETALL = "get_all";
    // 操作类型：获取全部数据（GET 请求）

    public final static String GTASK_JSON_ACTION_TYPE_MOVE = "move";
    // 操作类型：移动（将任务移到其他列表）

    public final static String GTASK_JSON_ACTION_TYPE_UPDATE = "update";
    // 操作类型：更新（PUT 请求）

    // ==================== Entity 实体相关字段 ====================
    public final static String GTASK_JSON_CREATOR_ID = "creator_id";
    // 创建者 ID，实践中通常设置为 "null"

    public final static String GTASK_JSON_CHILD_ENTITY = "child_entity";
    // 子实体（用于嵌套结构）

    public final static String GTASK_JSON_CLIENT_VERSION = "client_version";
    // 客户端版本号

    public final static String GTASK_JSON_COMPLETED = "completed";
    // 任务是否已完成（true / false）

    public final static String GTASK_JSON_CURRENT_LIST_ID = "current_list_id";
    // 当前任务列表 ID

    public final static String GTASK_JSON_DEFAULT_LIST_ID = "default_list_id";
    // 默认任务列表 ID

    public final static String GTASK_JSON_DELETED = "deleted";
    // 是否已删除（逻辑删除标记）

    public final static String GTASK_JSON_DEST_LIST = "dest_list";
    // 目标列表（用于移动操作）

    public final static String GTASK_JSON_DEST_PARENT = "dest_parent";
    // 目标父级（用于移动操作）

    public final static String GTASK_JSON_DEST_PARENT_TYPE = "dest_parent_type";
    // 目标父级类型（如 "GROUP"）

    public final static String GTASK_JSON_ENTITY_DELTA = "entity_delta";
    // 实体增量数据，包含创建或更新时需要传输的字段

    public final static String GTASK_JSON_ENTITY_TYPE = "entity_type";
    // 实体类型，取值为 GROUP 或 TASK

    public final static String GTASK_JSON_GET_DELETED = "get_deleted";
    // 是否获取已删除的实体

    public final static String GTASK_JSON_ID = "id";
    // 实体 ID（GID）

    public final static String GTASK_JSON_INDEX = "index";
    // 排序索引

    public final static String GTASK_JSON_LAST_MODIFIED = "last_modified";
    // 最后修改时间（时间戳）

    public final static String GTASK_JSON_LATEST_SYNC_POINT = "latest_sync_point";
    // 最新同步点（用于增量同步）

    public final static String GTASK_JSON_LIST_ID = "list_id";
    // 列表 ID

    public final static String GTASK_JSON_LISTS = "lists";
    // 列表集合（用于 get_all 响应）

    public final static String GTASK_JSON_NAME = "name";
    // 实体名称（任务标题或列表名称）

    public final static String GTASK_JSON_NEW_ID = "new_id";
    // 新 ID（用于 ID 变更场景）

    public final static String GTASK_JSON_NOTES = "notes";
    // 备注内容

    public final static String GTASK_JSON_PARENT_ID = "parent_id";
    // 父级 ID

    public final static String GTASK_JSON_PRIOR_SIBLING_ID = "prior_sibling_id";
    // 前一个兄弟实体的 ID（用于排序）

    public final static String GTASK_JSON_RESULTS = "results";
    // 响应结果集（用于 get_all 响应）

    public final static String GTASK_JSON_SOURCE_LIST = "source_list";
    // 源列表（用于移动操作）

    public final static String GTASK_JSON_TASKS = "tasks";
    // 任务集合（用于 get_all 响应）

    public final static String GTASK_JSON_TYPE = "type";
    // 类型字段

    public final static String GTASK_JSON_TYPE_GROUP = "GROUP";
    // 实体类型：分组（即任务列表）

    public final static String GTASK_JSON_TYPE_TASK = "TASK";
    // 实体类型：任务

    public final static String GTASK_JSON_USER = "user";
    // 用户标识

    // ==================== MIUI 文件夹相关常量 ====================
    // MIUI 系统在同步时会为文件夹名称添加此前缀，以便在 Google Tasks 中识别
    public final static String MIUI_FOLDER_PREFFIX = "[MIUI_Notes]";
    // MIUI 文件夹名称前缀

    // 系统预定义文件夹名称
    public final static String FOLDER_DEFAULT = "Default";
    // 默认系统文件夹名称

    public final static String FOLDER_CALL_NOTE = "Call_Note";
    // 通话记录文件夹名称

    public final static String FOLDER_META = "METADATA";
    // 元数据文件夹名称（用于存储同步映射信息）

    // ==================== 元数据（MetaData）相关键名 ====================
    // 元数据是同步过程中用于建立本地便签与云端任务/列表映射关系的特殊载体

    public final static String META_HEAD_GTASK_ID = "meta_gid";
    // 元数据中存储的关联 GTask ID（用于映射本地便签与云端笔记本）

    public final static String META_HEAD_NOTE = "meta_note";
    // 元数据中存储的 Note 信息（便签的主体数据）

    public final static String META_HEAD_DATA = "meta_data";
    // 元数据中存储的 Data 信息（便签的附加数据，如正文内容）

    public final static String META_NOTE_NAME = "[META INFO] DON'T UPDATE AND DELETE";
    // 元数据任务的固定标题，用于在云端识别该任务为元数据记录；提示用户不要手动修改或删除
}