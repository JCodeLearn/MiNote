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

// SqlNote 类：本地便签（Note 表）的 ORM 封装类
// 负责一条便签记录的读取、修改、序列化以及与 SqlData 的协同工作
package net.micode.notes.gtask.data;

import android.appwidget.AppWidgetManager;  // 桌面小部件管理器，获取无效小部件 ID 常量
import android.content.ContentResolver;     // 内容解析器，用于访问 ContentProvider
import android.content.ContentValues;       // 键值对容器，用于数据库增改操作
import android.content.Context;             // 上下文，获取资源和服务
import android.database.Cursor;             // 数据库游标，遍历查询结果
import android.net.Uri;                     // 统一资源标识符，定位数据表
import android.util.Log;                    // 日志工具

import net.micode.notes.data.Notes;                          // 便签相关常量（URI、类型定义等）
import net.micode.notes.data.Notes.DataColumns;              // Data 表字段名常量
import net.micode.notes.data.Notes.NoteColumns;              // Note 表字段名常量
import net.micode.notes.gtask.exception.ActionFailureException; // 同步操作失败异常
import net.micode.notes.tool.GTaskStringUtils;               // 字符串常量工具类
import net.micode.notes.tool.ResourceParser;                 // 资源解析工具（如默认背景色）

import org.json.JSONArray;        // JSON 数组，用于存储多条 Data 记录
import org.json.JSONException;    // JSON 解析异常
import org.json.JSONObject;       // JSON 对象，用于与云端交互

import java.util.ArrayList;       // 动态数组，存储该便签下的 SqlData 列表


public class SqlNote {
    // 日志标签，值为当前类名 "SqlNote"
    private static final String TAG = SqlNote.class.getSimpleName();

    // 无效 ID 常量，表示该记录尚未在数据库中获得有效 ID
    private static final int INVALID_ID = -99999;

    // ==================== 投影数组与列索引常量 ====================
    // 定义从 Note 表查询时需要的全部字段列表
    public static final String[] PROJECTION_NOTE = new String[] {
            NoteColumns.ID,               // 便签 ID
            NoteColumns.ALERTED_DATE,     // 提醒日期
            NoteColumns.BG_COLOR_ID,      // 背景颜色 ID
            NoteColumns.CREATED_DATE,     // 创建日期
            NoteColumns.HAS_ATTACHMENT,   // 是否有附件
            NoteColumns.MODIFIED_DATE,    // 最后修改日期
            NoteColumns.NOTES_COUNT,      // 便签数量（用于文件夹）
            NoteColumns.PARENT_ID,        // 父文件夹 ID
            NoteColumns.SNIPPET,          // 内容摘要
            NoteColumns.TYPE,             // 类型（便签/文件夹/系统）
            NoteColumns.WIDGET_ID,        // 桌面小部件 ID
            NoteColumns.WIDGET_TYPE,      // 桌面小部件类型
            NoteColumns.SYNC_ID,          // 同步 ID
            NoteColumns.LOCAL_MODIFIED,   // 本地是否已修改
            NoteColumns.ORIGIN_PARENT_ID, // 原始父文件夹 ID
            NoteColumns.GTASK_ID,         // Google Tasks ID
            NoteColumns.VERSION           // 版本号（乐观锁）
    };

    // 以下常量与 PROJECTION_NOTE 数组的索引一一对应，方便从 Cursor 中按位置取值
    public static final int ID_COLUMN = 0;
    public static final int ALERTED_DATE_COLUMN = 1;
    public static final int BG_COLOR_ID_COLUMN = 2;
    public static final int CREATED_DATE_COLUMN = 3;
    public static final int HAS_ATTACHMENT_COLUMN = 4;
    public static final int MODIFIED_DATE_COLUMN = 5;
    public static final int NOTES_COUNT_COLUMN = 6;
    public static final int PARENT_ID_COLUMN = 7;
    public static final int SNIPPET_COLUMN = 8;
    public static final int TYPE_COLUMN = 9;
    public static final int WIDGET_ID_COLUMN = 10;
    public static final int WIDGET_TYPE_COLUMN = 11;
    public static final int SYNC_ID_COLUMN = 12;
    public static final int LOCAL_MODIFIED_COLUMN = 13;
    public static final int ORIGIN_PARENT_ID_COLUMN = 14;
    public static final int GTASK_ID_COLUMN = 15;
    public static final int VERSION_COLUMN = 16;

    // ==================== 成员变量 ====================
    private Context mContext;                    // 上下文对象
    private ContentResolver mContentResolver;    // 内容解析器
    private boolean mIsCreate;                   // 标记当前对象是新建还是从数据库加载
    private long mId;                            // 便签 ID
    private long mAlertDate;                     // 提醒日期（时间戳）
    private int mBgColorId;                      // 背景颜色 ID
    private long mCreatedDate;                   // 创建日期（时间戳）
    private int mHasAttachment;                  // 是否有附件（0/1）
    private long mModifiedDate;                  // 最后修改日期（时间戳）
    private long mParentId;                      // 父文件夹 ID
    private String mSnippet;                     // 内容摘要
    private int mType;                           // 类型（便签/文件夹/系统）
    private int mWidgetId;                       // 桌面小部件 ID
    private int mWidgetType;                     // 桌面小部件类型
    private long mOriginParent;                  // 原始父文件夹 ID
    private long mVersion;                       // 版本号（乐观锁）
    private ContentValues mDiffNoteValues;       // 增量更新容器，只记录被修改的字段
    private ArrayList<SqlData> mDataList;        // 该便签下的所有 Data 记录列表

    // ==================== 构造方法 ====================

    /**
     * 构造方法一：创建一个全新的 SqlNote 对象（对应一条待插入的新便签）
     * @param context 上下文
     */
    public SqlNote(Context context) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mIsCreate = true;                                       // 标记为新建
        mId = INVALID_ID;                                       // ID 暂时无效
        mAlertDate = 0;                                         // 提醒日期默认为 0
        mBgColorId = ResourceParser.getDefaultBgId(context);    // 使用默认背景色
        mCreatedDate = System.currentTimeMillis();              // 创建时间设为当前时间
        mHasAttachment = 0;                                     // 默认无附件
        mModifiedDate = System.currentTimeMillis();             // 修改时间设为当前时间
        mParentId = 0;                                          // 默认无父文件夹
        mSnippet = "";                                          // 摘要为空
        mType = Notes.TYPE_NOTE;                                // 默认类型为"便签"
        mWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;      // 无效小部件 ID
        mWidgetType = Notes.TYPE_WIDGET_INVALIDE;               // 无效小部件类型
        mOriginParent = 0;                                      // 原始父文件夹为 0
        mVersion = 0;                                           // 版本号从 0 开始
        mDiffNoteValues = new ContentValues();                  // 初始化增量容器
        mDataList = new ArrayList<SqlData>();                   // 初始化 Data 列表
    }

    /**
     * 构造方法二：从数据库游标加载一条已存在的便签
     * @param context 上下文
     * @param c       指向数据库查询结果的游标
     */
    public SqlNote(Context context, Cursor c) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mIsCreate = false;                      // 标记为已有对象
        loadFromCursor(c);                      // 从游标读取数据填充字段
        mDataList = new ArrayList<SqlData>();
        if (mType == Notes.TYPE_NOTE)           // 如果是便签类型，加载其 Data 记录
            loadDataContent();
        mDiffNoteValues = new ContentValues();  // 初始化增量容器
    }

    /**
     * 构造方法三：通过便签 ID 从数据库加载一条已有便签
     * @param context 上下文
     * @param id      便签 ID
     */
    public SqlNote(Context context, long id) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mIsCreate = false;                      // 标记为已有对象
        loadFromCursor(id);                     // 根据 ID 查询并填充字段
        mDataList = new ArrayList<SqlData>();
        if (mType == Notes.TYPE_NOTE)           // 如果是便签类型，加载其 Data 记录
            loadDataContent();
        mDiffNoteValues = new ContentValues();  // 初始化增量容器
    }

    // ==================== 数据加载方法 ====================

    /**
     * 通过便签 ID 从数据库查询并加载数据
     * @param id 便签 ID
     */
    private void loadFromCursor(long id) {
        Cursor c = null;
        try {
            // 查询指定 ID 的便签记录
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, PROJECTION_NOTE, "(_id=?)",
                    new String[] {
                            String.valueOf(id)
                    }, null);
            if (c != null) {
                c.moveToNext();         // 移动到第一条（也是唯一一条）记录
                loadFromCursor(c);      // 调用重载方法填充字段
            } else {
                Log.w(TAG, "loadFromCursor: cursor = null");
            }
        } finally {
            if (c != null)
                c.close();              // 确保关闭游标，释放资源
        }
    }

    /**
     * 从游标的当前行中取出各字段值，赋给成员变量
     * @param c 数据库游标
     */
    private void loadFromCursor(Cursor c) {
        mId = c.getLong(ID_COLUMN);
        mAlertDate = c.getLong(ALERTED_DATE_COLUMN);
        mBgColorId = c.getInt(BG_COLOR_ID_COLUMN);
        mCreatedDate = c.getLong(CREATED_DATE_COLUMN);
        mHasAttachment = c.getInt(HAS_ATTACHMENT_COLUMN);
        mModifiedDate = c.getLong(MODIFIED_DATE_COLUMN);
        mParentId = c.getLong(PARENT_ID_COLUMN);
        mSnippet = c.getString(SNIPPET_COLUMN);
        mType = c.getInt(TYPE_COLUMN);
        mWidgetId = c.getInt(WIDGET_ID_COLUMN);
        mWidgetType = c.getInt(WIDGET_TYPE_COLUMN);
        mVersion = c.getLong(VERSION_COLUMN);
    }

    /**
     * 加载该便签下所有的 Data 记录（如正文、附件信息等）
     */
    private void loadDataContent() {
        Cursor c = null;
        mDataList.clear();      // 清空旧数据
        try {
            // 查询属于当前便签 ID 的所有 Data 记录
            c = mContentResolver.query(Notes.CONTENT_DATA_URI, SqlData.PROJECTION_DATA,
                    "(note_id=?)", new String[] {
                            String.valueOf(mId)
                    }, null);
            if (c != null) {
                if (c.getCount() == 0) {
                    Log.w(TAG, "it seems that the note has not data");
                    return;
                }
                // 遍历所有 Data 记录，创建 SqlData 对象并加入列表
                while (c.moveToNext()) {
                    SqlData data = new SqlData(mContext, c);
                    mDataList.add(data);
                }
            } else {
                Log.w(TAG, "loadDataContent: cursor = null");
            }
        } finally {
            if (c != null)
                c.close();      // 关闭游标
        }
    }

    // ==================== 核心方法：JSON 互转 ====================

    /**
     * 用云端同步下来的 JSON 数据更新当前便签对象及其 Data 列表。
     * 该方法会比较 JSON 中的值与现有值，仅将变化的字段记录到 mDiffNoteValues，实现增量更新。
     *
     * @param js 来自云端的 JSON 对象，包含便签信息（note）和数据列表（data）
     * @return true 表示设置成功，false 表示失败
     */
    public boolean setContent(JSONObject js) {
        try {
            // 获取便签主体 JSON
            JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);

            if (note.getInt(NoteColumns.TYPE) == Notes.TYPE_SYSTEM) {
                // 系统文件夹不允许修改
                Log.w(TAG, "cannot set system folder");

            } else if (note.getInt(NoteColumns.TYPE) == Notes.TYPE_FOLDER) {
                // --- 文件夹类型：只能更新摘要和类型 ---
                String snippet = note.has(NoteColumns.SNIPPET) ? note
                        .getString(NoteColumns.SNIPPET) : "";
                if (mIsCreate || !mSnippet.equals(snippet)) {
                    mDiffNoteValues.put(NoteColumns.SNIPPET, snippet);
                }
                mSnippet = snippet;

                int type = note.has(NoteColumns.TYPE) ? note.getInt(NoteColumns.TYPE)
                        : Notes.TYPE_NOTE;
                if (mIsCreate || mType != type) {
                    mDiffNoteValues.put(NoteColumns.TYPE, type);
                }
                mType = type;

            } else if (note.getInt(NoteColumns.TYPE) == Notes.TYPE_NOTE) {
                // --- 便签类型：更新所有字段，并同步 Data 列表 ---
                JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);

                // 逐字段处理（增量更新模式：仅记录变化的字段）
                long id = note.has(NoteColumns.ID) ? note.getLong(NoteColumns.ID) : INVALID_ID;
                if (mIsCreate || mId != id) {
                    mDiffNoteValues.put(NoteColumns.ID, id);
                }
                mId = id;

                long alertDate = note.has(NoteColumns.ALERTED_DATE) ? note
                        .getLong(NoteColumns.ALERTED_DATE) : 0;
                if (mIsCreate || mAlertDate != alertDate) {
                    mDiffNoteValues.put(NoteColumns.ALERTED_DATE, alertDate);
                }
                mAlertDate = alertDate;

                int bgColorId = note.has(NoteColumns.BG_COLOR_ID) ? note
                        .getInt(NoteColumns.BG_COLOR_ID) : ResourceParser.getDefaultBgId(mContext);
                if (mIsCreate || mBgColorId != bgColorId) {
                    mDiffNoteValues.put(NoteColumns.BG_COLOR_ID, bgColorId);
                }
                mBgColorId = bgColorId;

                long createDate = note.has(NoteColumns.CREATED_DATE) ? note
                        .getLong(NoteColumns.CREATED_DATE) : System.currentTimeMillis();
                if (mIsCreate || mCreatedDate != createDate) {
                    mDiffNoteValues.put(NoteColumns.CREATED_DATE, createDate);
                }
                mCreatedDate = createDate;

                int hasAttachment = note.has(NoteColumns.HAS_ATTACHMENT) ? note
                        .getInt(NoteColumns.HAS_ATTACHMENT) : 0;
                if (mIsCreate || mHasAttachment != hasAttachment) {
                    mDiffNoteValues.put(NoteColumns.HAS_ATTACHMENT, hasAttachment);
                }
                mHasAttachment = hasAttachment;

                long modifiedDate = note.has(NoteColumns.MODIFIED_DATE) ? note
                        .getLong(NoteColumns.MODIFIED_DATE) : System.currentTimeMillis();
                if (mIsCreate || mModifiedDate != modifiedDate) {
                    mDiffNoteValues.put(NoteColumns.MODIFIED_DATE, modifiedDate);
                }
                mModifiedDate = modifiedDate;

                long parentId = note.has(NoteColumns.PARENT_ID) ? note
                        .getLong(NoteColumns.PARENT_ID) : 0;
                if (mIsCreate || mParentId != parentId) {
                    mDiffNoteValues.put(NoteColumns.PARENT_ID, parentId);
                }
                mParentId = parentId;

                String snippet = note.has(NoteColumns.SNIPPET) ? note
                        .getString(NoteColumns.SNIPPET) : "";
                if (mIsCreate || !mSnippet.equals(snippet)) {
                    mDiffNoteValues.put(NoteColumns.SNIPPET, snippet);
                }
                mSnippet = snippet;

                int type = note.has(NoteColumns.TYPE) ? note.getInt(NoteColumns.TYPE)
                        : Notes.TYPE_NOTE;
                if (mIsCreate || mType != type) {
                    mDiffNoteValues.put(NoteColumns.TYPE, type);
                }
                mType = type;

                int widgetId = note.has(NoteColumns.WIDGET_ID) ? note.getInt(NoteColumns.WIDGET_ID)
                        : AppWidgetManager.INVALID_APPWIDGET_ID;
                if (mIsCreate || mWidgetId != widgetId) {
                    mDiffNoteValues.put(NoteColumns.WIDGET_ID, widgetId);
                }
                mWidgetId = widgetId;

                int widgetType = note.has(NoteColumns.WIDGET_TYPE) ? note
                        .getInt(NoteColumns.WIDGET_TYPE) : Notes.TYPE_WIDGET_INVALIDE;
                if (mIsCreate || mWidgetType != widgetType) {
                    mDiffNoteValues.put(NoteColumns.WIDGET_TYPE, widgetType);
                }
                mWidgetType = widgetType;

                long originParent = note.has(NoteColumns.ORIGIN_PARENT_ID) ? note
                        .getLong(NoteColumns.ORIGIN_PARENT_ID) : 0;
                if (mIsCreate || mOriginParent != originParent) {
                    mDiffNoteValues.put(NoteColumns.ORIGIN_PARENT_ID, originParent);
                }
                mOriginParent = originParent;

                // 处理 Data 列表：逐个更新或新建 SqlData 对象
                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject data = dataArray.getJSONObject(i);
                    SqlData sqlData = null;

                    // 如果 JSON 中有 ID，尝试在现有列表中找到对应的 SqlData
                    if (data.has(DataColumns.ID)) {
                        long dataId = data.getLong(DataColumns.ID);
                        for (SqlData temp : mDataList) {
                            if (dataId == temp.getId()) {
                                sqlData = temp;
                            }
                        }
                    }

                    // 如果没有找到，则创建新的 SqlData 并加入列表
                    if (sqlData == null) {
                        sqlData = new SqlData(mContext);
                        mDataList.add(sqlData);
                    }

                    // 用 JSON 数据更新 SqlData 对象（增量更新）
                    sqlData.setContent(data);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 将当前 SqlNote 对象及其 Data 列表转换为 JSON 对象，用于上传到云端。
     * 注意：只有已存在于数据库中的记录（非新建）才能生成有效的 JSON。
     *
     * @return 包含便签信息和 Data 数组的 JSONObject，失败则返回 null
     */
    public JSONObject getContent() {
        try {
            JSONObject js = new JSONObject();

            if (mIsCreate) {
                Log.e(TAG, "it seems that we haven't created this in database yet");
                return null;
            }

            JSONObject note = new JSONObject();

            if (mType == Notes.TYPE_NOTE) {
                // --- 便签类型：序列化所有字段，并包含 Data 数组 ---
                note.put(NoteColumns.ID, mId);
                note.put(NoteColumns.ALERTED_DATE, mAlertDate);
                note.put(NoteColumns.BG_COLOR_ID, mBgColorId);
                note.put(NoteColumns.CREATED_DATE, mCreatedDate);
                note.put(NoteColumns.HAS_ATTACHMENT, mHasAttachment);
                note.put(NoteColumns.MODIFIED_DATE, mModifiedDate);
                note.put(NoteColumns.PARENT_ID, mParentId);
                note.put(NoteColumns.SNIPPET, mSnippet);
                note.put(NoteColumns.TYPE, mType);
                note.put(NoteColumns.WIDGET_ID, mWidgetId);
                note.put(NoteColumns.WIDGET_TYPE, mWidgetType);
                note.put(NoteColumns.ORIGIN_PARENT_ID, mOriginParent);
                js.put(GTaskStringUtils.META_HEAD_NOTE, note);

                // 遍历 Data 列表，生成 JSON 数组
                JSONArray dataArray = new JSONArray();
                for (SqlData sqlData : mDataList) {
                    JSONObject data = sqlData.getContent();
                    if (data != null) {
                        dataArray.put(data);
                    }
                }
                js.put(GTaskStringUtils.META_HEAD_DATA, dataArray);

            } else if (mType == Notes.TYPE_FOLDER || mType == Notes.TYPE_SYSTEM) {
                // --- 文件夹/系统类型：只序列化 ID、类型和摘要 ---
                note.put(NoteColumns.ID, mId);
                note.put(NoteColumns.TYPE, mType);
                note.put(NoteColumns.SNIPPET, mSnippet);
                js.put(GTaskStringUtils.META_HEAD_NOTE, note);
            }

            return js;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
        return null;
    }

    // ==================== 辅助 Setter 方法 ====================

    /**
     * 设置父文件夹 ID
     * @param id 父文件夹 ID
     */
    public void setParentId(long id) {
        mParentId = id;
        mDiffNoteValues.put(NoteColumns.PARENT_ID, id);
    }

    /**
     * 设置 Google Tasks ID
     * @param gid Google Tasks 任务 ID
     */
    public void setGtaskId(String gid) {
        mDiffNoteValues.put(NoteColumns.GTASK_ID, gid);
    }

    /**
     * 设置同步 ID
     * @param syncId 同步 ID
     */
    public void setSyncId(long syncId) {
        mDiffNoteValues.put(NoteColumns.SYNC_ID, syncId);
    }

    /**
     * 重置本地修改标记（表示本地已与云端同步）
     */
    public void resetLocalModified() {
        mDiffNoteValues.put(NoteColumns.LOCAL_MODIFIED, 0);
    }

    // ==================== Getter 方法 ====================

    public long getId() {
        return mId;
    }

    public long getParentId() {
        return mParentId;
    }

    public String getSnippet() {
        return mSnippet;
    }

    /**
     * 判断当前对象是否为便签类型
     * @return true 表示是便签，false 表示是文件夹或系统类型
     */
    public boolean isNoteType() {
        return mType == Notes.TYPE_NOTE;
    }

    // ==================== 核心方法：提交到数据库 ====================

    /**
     * 将当前对象的所有改动提交到本地数据库。
     * 对于新建对象执行 INSERT，对于已有对象执行 UPDATE。
     * 支持乐观锁版本校验，防止并发修改冲突。
     *
     * @param validateVersion 是否进行版本校验
     */
    public void commit(boolean validateVersion) {
        if (mIsCreate) {
            // ========== 新建对象：执行 INSERT 操作 ==========
            // 如果 ID 无效且增量容器中误包含了 ID，则移除它（ID 由数据库自动生成）
            if (mId == INVALID_ID && mDiffNoteValues.containsKey(NoteColumns.ID)) {
                mDiffNoteValues.remove(NoteColumns.ID);
            }

            // 执行插入，返回新记录的 URI
            Uri uri = mContentResolver.insert(Notes.CONTENT_NOTE_URI, mDiffNoteValues);
            try {
                // 从 URI 中解析出数据库生成的自增 ID
                mId = Long.valueOf(uri.getPathSegments().get(1));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Get note id error :" + e.toString());
                throw new ActionFailureException("create note failed");
            }
            if (mId == 0) {
                throw new IllegalStateException("Create thread id failed");
            }

            // 如果是便签类型，还要提交其下所有 Data 记录
            if (mType == Notes.TYPE_NOTE) {
                for (SqlData sqlData : mDataList) {
                    sqlData.commit(mId, false, -1);     // 新建 Data 不校验版本
                }
            }

        } else {
            // ========== 已有对象：执行 UPDATE 操作 ==========
            // 安全检查：无效 ID 不允许更新
            if (mId <= 0 && mId != Notes.ID_ROOT_FOLDER && mId != Notes.ID_CALL_RECORD_FOLDER) {
                Log.e(TAG, "No such note");
                throw new IllegalStateException("Try to update note with invalid id");
            }

            if (mDiffNoteValues.size() > 0) {
                mVersion++;     // 版本号自增
                int result = 0;

                if (!validateVersion) {
                    // 不校验版本，直接更新
                    result = mContentResolver.update(Notes.CONTENT_NOTE_URI, mDiffNoteValues, "("
                            + NoteColumns.ID + "=?)", new String[] {
                            String.valueOf(mId)
                    });
                } else {
                    // 校验版本：只有当本地版本号 >= 数据库中的版本号时才更新（乐观锁）
                    result = mContentResolver.update(Notes.CONTENT_NOTE_URI, mDiffNoteValues, "("
                                    + NoteColumns.ID + "=?) AND (" + NoteColumns.VERSION + "<=?)",
                            new String[] {
                                    String.valueOf(mId), String.valueOf(mVersion)
                            });
                }

                // 如果受影响的行数为 0，说明没有实际更新（可能版本冲突）
                if (result == 0) {
                    Log.w(TAG, "there is no update. maybe user updates note when syncing");
                }
            }

            // 如果是便签类型，还要提交其下所有 Data 记录
            if (mType == Notes.TYPE_NOTE) {
                for (SqlData sqlData : mDataList) {
                    sqlData.commit(mId, validateVersion, mVersion);
                }
            }
        }

        // ========== 提交后刷新本地数据 ==========
        // 重新从数据库加载最新的便签信息，确保内存数据与数据库一致
        loadFromCursor(mId);
        if (mType == Notes.TYPE_NOTE)
            loadDataContent();      // 重新加载 Data 列表

        // 清空增量容器，并将对象标记为已持久化
        mDiffNoteValues.clear();
        mIsCreate = false;
    }
}