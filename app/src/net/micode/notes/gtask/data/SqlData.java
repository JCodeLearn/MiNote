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

// 声明该类所属的包路径，位于 gtask 同步模块的数据层
package net.micode.notes.gtask.data;

// 导入 Android 内容解析器相关类，用于访问本地数据库
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

// 导入项目内部便签数据常量类
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;
// 导入同步异常类
import net.micode.notes.gtask.exception.ActionFailureException;

// 导入 JSON 处理相关类
import org.json.JSONException;
import org.json.JSONObject;

/**
 * SqlData 类：封装对本地数据库 "Data" 表中一条记录的操作。
 * 在小米便签中，一条便签（Note）可能包含多条 Data 记录（如正文、附件信息等），
 * 本类负责单条 Data 记录的读取、修改、序列化以及与云端的同步。
 */
public class SqlData {
    // 日志标签，值为当前类名 "SqlData"，便于调试
    private static final String TAG = SqlData.class.getSimpleName();

    // 无效 ID 常量，表示该记录尚未在数据库中获得有效 ID
    private static final int INVALID_ID = -99999;

    /**
     * 定义从 Data 表查询时需要的字段列表。
     * 包含：ID、MIME类型、内容、通用字段1、通用字段3
     */
    public static final String[] PROJECTION_DATA = new String[] {
            DataColumns.ID, DataColumns.MIME_TYPE, DataColumns.CONTENT, DataColumns.DATA1,
            DataColumns.DATA3
    };

    // 以下常量与 PROJECTION_DATA 数组的索引一一对应，方便从 Cursor 中按位置取值
    public static final int DATA_ID_COLUMN = 0;                // ID 字段的列索引
    public static final int DATA_MIME_TYPE_COLUMN = 1;        // MIME 类型的列索引
    public static final int DATA_CONTENT_COLUMN = 2;          // 内容字段的列索引
    public static final int DATA_CONTENT_DATA_1_COLUMN = 3;   // 通用字段1的列索引
    public static final int DATA_CONTENT_DATA_3_COLUMN = 4;   // 通用字段3的列索引

    // --- 成员变量 -------------------------------------------------
    private ContentResolver mContentResolver;   // 内容解析器，用于访问 ContentProvider
    private boolean mIsCreate;                  // 标记当前对象是新建还是从数据库加载
    private long mDataId;                       // 该 Data 记录在数据库中的主键 ID
    private String mDataMimeType;               // 数据的 MIME 类型（例如便签正文）
    private String mDataContent;                // 数据内容（例如便签文本）
    private long mDataContentData1;             // 通用整型字段1（根据 MIME 类型含义不同）
    private String mDataContentData3;           // 通用字符串字段3（扩展信息）
    private ContentValues mDiffDataValues;      // 增量更新容器，只记录被修改过的字段

    // --- 构造方法 -------------------------------------------------

    /**
     * 构造方法一：创建一个全新的 SqlData 对象（对应一条待插入的新记录）
     * @param context 上下文，用于获取 ContentResolver
     */
    public SqlData(Context context) {
        mContentResolver = context.getContentResolver();
        mIsCreate = true;                       // 标记为新建对象
        mDataId = INVALID_ID;                   // ID 暂时无效
        mDataMimeType = DataConstants.NOTE;     // 默认 MIME 类型为便签正文
        mDataContent = "";                      // 内容默认为空字符串
        mDataContentData1 = 0;                  // 通用字段1默认为0
        mDataContentData3 = "";                 // 通用字段3默认为空
        mDiffDataValues = new ContentValues();  // 初始化增量更新容器
    }

    /**
     * 构造方法二：从数据库游标加载一条已存在的记录
     * @param context 上下文
     * @param c       指向数据库查询结果的游标，当前位置即为要加载的记录
     */
    public SqlData(Context context, Cursor c) {
        mContentResolver = context.getContentResolver();
        mIsCreate = false;                      // 标记为已有对象
        loadFromCursor(c);                      // 从游标中读取数据填充字段
        mDiffDataValues = new ContentValues();  // 初始化增量更新容器
    }

    /**
     * 私有方法：从游标的当前行中取出各字段值，赋给成员变量
     * @param c 数据库游标
     */
    private void loadFromCursor(Cursor c) {
        mDataId = c.getLong(DATA_ID_COLUMN);
        mDataMimeType = c.getString(DATA_MIME_TYPE_COLUMN);
        mDataContent = c.getString(DATA_CONTENT_COLUMN);
        mDataContentData1 = c.getLong(DATA_CONTENT_DATA_1_COLUMN);
        mDataContentData3 = c.getString(DATA_CONTENT_DATA_3_COLUMN);
    }

    // --- 核心方法 -------------------------------------------------

    /**
     * 用云端同步下来的 JSON 数据更新当前对象的内容。
     * 该方法会比较 JSON 中的值与现有值，仅将变化的字段记录到 mDiffDataValues 中，
     * 实现增量更新。
     * @param js 来自云端的 JSON 对象，包含 Data 记录的各个字段
     * @throws JSONException 如果 JSON 解析出错
     */
    public void setContent(JSONObject js) throws JSONException {
        // 处理 ID 字段：如果 JSON 中有 ID 则使用，否则设为无效 ID
        long dataId = js.has(DataColumns.ID) ? js.getLong(DataColumns.ID) : INVALID_ID;
        // 如果是新建对象或 ID 发生变化，则将新 ID 放入增量容器
        if (mIsCreate || mDataId != dataId) {
            mDiffDataValues.put(DataColumns.ID, dataId);
        }
        mDataId = dataId;

        // 处理 MIME 类型字段
        String dataMimeType = js.has(DataColumns.MIME_TYPE) ? js.getString(DataColumns.MIME_TYPE)
                : DataConstants.NOTE;
        if (mIsCreate || !mDataMimeType.equals(dataMimeType)) {
            mDiffDataValues.put(DataColumns.MIME_TYPE, dataMimeType);
        }
        mDataMimeType = dataMimeType;

        // 处理内容字段
        String dataContent = js.has(DataColumns.CONTENT) ? js.getString(DataColumns.CONTENT) : "";
        if (mIsCreate || !mDataContent.equals(dataContent)) {
            mDiffDataValues.put(DataColumns.CONTENT, dataContent);
        }
        mDataContent = dataContent;

        // 处理通用字段1（DATA1）
        long dataContentData1 = js.has(DataColumns.DATA1) ? js.getLong(DataColumns.DATA1) : 0;
        if (mIsCreate || mDataContentData1 != dataContentData1) {
            mDiffDataValues.put(DataColumns.DATA1, dataContentData1);
        }
        mDataContentData1 = dataContentData1;

        // 处理通用字段3（DATA3）
        String dataContentData3 = js.has(DataColumns.DATA3) ? js.getString(DataColumns.DATA3) : "";
        if (mIsCreate || !mDataContentData3.equals(dataContentData3)) {
            mDiffDataValues.put(DataColumns.DATA3, dataContentData3);
        }
        mDataContentData3 = dataContentData3;
    }

    /**
     * 将当前 SqlData 对象的内容转换为 JSON 对象，用于上传到云端或本地备份。
     * 注意：只有已存在于数据库中的记录（非新建）才能生成有效的 JSON。
     * @return 包含当前记录所有字段的 JSONObject，若为新建对象则返回 null
     * @throws JSONException 如果 JSON 构建出错
     */
    public JSONObject getContent() throws JSONException {
        if (mIsCreate) {
            Log.e(TAG, "it seems that we haven't created this in database yet");
            return null;
        }
        JSONObject js = new JSONObject();
        js.put(DataColumns.ID, mDataId);
        js.put(DataColumns.MIME_TYPE, mDataMimeType);
        js.put(DataColumns.CONTENT, mDataContent);
        js.put(DataColumns.DATA1, mDataContentData1);
        js.put(DataColumns.DATA3, mDataContentData3);
        return js;
    }

    /**
     * 将当前对象的所有改动提交到本地数据库。
     * 对于新建对象执行 INSERT，对于已有对象执行 UPDATE。
     * 支持乐观锁版本校验，防止并发修改冲突。
     * @param noteId         该 Data 记录所属的便签 ID
     * @param validateVersion 是否进行版本校验
     * @param version        期望的便签版本号（仅在 validateVersion 为 true 时有效）
     */
    public void commit(long noteId, boolean validateVersion, long version) {
        if (mIsCreate) {
            // --- 新建对象：执行 INSERT 操作 ---
            // 如果 ID 无效且增量容器中误包含了 ID，则移除它（ID 由数据库自动生成）
            if (mDataId == INVALID_ID && mDiffDataValues.containsKey(DataColumns.ID)) {
                mDiffDataValues.remove(DataColumns.ID);
            }
            // 关联所属便签的 ID
            mDiffDataValues.put(DataColumns.NOTE_ID, noteId);
            // 执行插入，返回新记录的 URI
            Uri uri = mContentResolver.insert(Notes.CONTENT_DATA_URI, mDiffDataValues);
            try {
                // 从 URI 中解析出数据库生成的自增 ID
                mDataId = Long.valueOf(uri.getPathSegments().get(1));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Get note id error :" + e.toString());
                throw new ActionFailureException("create note failed");
            }
        } else {
            // --- 已有对象：执行 UPDATE 操作 ---
            if (mDiffDataValues.size() > 0) {
                int result = 0;
                if (!validateVersion) {
                    // 不校验版本，直接更新
                    result = mContentResolver.update(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mDataId), mDiffDataValues, null, null);
                } else {
                    // 校验版本：只有当所属便签的版本号与传入的 version 一致时才更新
                    // 使用子查询检查 NOTE 表中的 VERSION 字段，实现乐观锁
                    result = mContentResolver.update(ContentUris.withAppendedId(
                                    Notes.CONTENT_DATA_URI, mDataId), mDiffDataValues,
                            " ? in (SELECT " + NoteColumns.ID + " FROM " + TABLE.NOTE
                                    + " WHERE " + NoteColumns.VERSION + "=?)", new String[] {
                                    String.valueOf(noteId), String.valueOf(version)
                            });
                }
                // 如果受影响的行数为0，说明没有实际更新（可能因为版本冲突或记录不存在）
                if (result == 0) {
                    Log.w(TAG, "there is no update. maybe user updates note when syncing");
                }
            }
        }

        // 清空增量容器，并将对象标记为已持久化（非新建）
        mDiffDataValues.clear();
        mIsCreate = false;
    }

    /**
     * 获取当前 Data 记录的数据库 ID
     * @return 记录 ID
     */
    public long getId() {
        return mDataId;
    }
}