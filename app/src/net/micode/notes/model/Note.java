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

package net.micode.notes.model;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;

import java.util.ArrayList;

/**
 * 笔记数据模型类。
 * 封装笔记的创建、属性设置、差异更新和数据同步到数据库的核心逻辑。
 * 采用差异更新机制，只记录被修改的字段，减少数据库写入量。
 */
public class Note {
    // 笔记属性差异值（只存放被修改的字段）
    private ContentValues mNoteDiffValues;
    // 笔记的详细数据管理对象（文本和通话记录）
    private NoteData mNoteData;
    private static final String TAG = "Note";

    /**
     * 在数据库中创建一个新笔记，返回新笔记的 ID。
     * 该方法为同步方法，防止并发创建时 ID 冲突。
     *
     * @param context  上下文，用于获取 ContentResolver
     * @param folderId 新笔记所属的文件夹 ID
     * @return 新创建笔记的 ID，失败时抛出异常
     */
    public static synchronized long getNewNoteId(Context context, long folderId) {
        // 构建新笔记的初始数据
        ContentValues values = new ContentValues();
        long createdTime = System.currentTimeMillis();
        values.put(NoteColumns.CREATED_DATE, createdTime);
        values.put(NoteColumns.MODIFIED_DATE, createdTime);
        values.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
        values.put(NoteColumns.LOCAL_MODIFIED, 1);    // 新笔记标记为已修改（待同步）
        values.put(NoteColumns.PARENT_ID, folderId);
        Uri uri = context.getContentResolver().insert(Notes.CONTENT_NOTE_URI, values);

        long noteId = 0;
        try {
            // 从返回 URI 中解析出笔记 ID
            noteId = Long.valueOf(uri.getPathSegments().get(1));
        } catch (NumberFormatException e) {
            Log.e(TAG, "Get note id error :" + e.toString());
            noteId = 0;
        }
        if (noteId == -1) {
            throw new IllegalStateException("Wrong note id:" + noteId);
        }
        return noteId;
    }

    /**
     * 构造方法，初始化差异值容器和详细数据管理器。
     */
    public Note() {
        mNoteDiffValues = new ContentValues();
        mNoteData = new NoteData();
    }

    /**
     * 设置笔记属性值（如背景色、提醒时间等）。
     * 会自动标记本地修改标志和更新修改时间。
     *
     * @param key   字段名
     * @param value 字段值
     */
    public void setNoteValue(String key, String value) {
        mNoteDiffValues.put(key, value);
        mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
        mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
    }

    /**
     * 设置文本数据的字段值。
     *
     * @param key   字段名
     * @param value 字段值
     */
    public void setTextData(String key, String value) {
        mNoteData.setTextData(key, value);
    }

    /**
     * 设置文本数据记录在 data 表中的 ID（用于更新已有记录）。
     *
     * @param id 文本数据记录 ID
     */
    public void setTextDataId(long id) {
        mNoteData.setTextDataId(id);
    }

    /**
     * 获取文本数据记录在 data 表中的 ID。
     *
     * @return 文本数据记录 ID
     */
    public long getTextDataId() {
        return mNoteData.mTextDataId;
    }

    /**
     * 设置通话记录数据在 data 表中的 ID（用于更新已有记录）。
     *
     * @param id 通话记录数据 ID
     */
    public void setCallDataId(long id) {
        mNoteData.setCallDataId(id);
    }

    /**
     * 设置通话记录数据的字段值。
     *
     * @param key   字段名
     * @param value 字段值
     */
    public void setCallData(String key, String value) {
        mNoteData.setCallData(key, value);
    }

    /**
     * 判断笔记是否有本地修改（笔记属性或详细数据有变更）。
     *
     * @return true 表示有未同步的本地修改
     */
    public boolean isLocalModified() {
        return mNoteDiffValues.size() > 0 || mNoteData.isLocalModified();
    }

    /**
     * 将笔记的差异数据同步到数据库。
     * 先更新 note 表的属性字段，再将 data 表的变更通过批量操作一次性写入。
     * 即使 note 表更新失败，也会继续尝试提交 data 表变更以保证数据安全。
     *
     * @param context 上下文
     * @param noteId  需要同步的笔记 ID
     * @return true 表示同步成功，false 表示失败
     */
    public boolean syncNote(Context context, long noteId) {
        if (noteId <= 0) {
            throw new IllegalArgumentException("Wrong note id:" + noteId);
        }

        if (!isLocalModified()) {
            return true;
        }

        /**
         * 理论上，一旦数据发生改变，笔记的 LOCAL_MODIFIED 和 MODIFIED_DATE 就应该更新。
         * 为了数据安全，即使更新笔记失败，也继续更新 data 表。
         */
        if (context.getContentResolver().update(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), mNoteDiffValues, null,
                null) == 0) {
            Log.e(TAG, "Update note error, should not happen");
            // 不直接返回，继续执行后续操作
        }
        mNoteDiffValues.clear();

        if (mNoteData.isLocalModified()
                && (mNoteData.pushIntoContentResolver(context, noteId) == null)) {
            return false;
        }

        return true;
    }

    /**
     * 笔记详细数据管理内部类。
     * 负责管理文本数据（TextNote）和通话记录数据（CallNote）的 ContentValues 差异值，
     * 并根据是否有已有 ID 决定执行插入或更新操作。
     */
    private class NoteData {
        // 文本数据在 data 表中的 ID（0 表示待插入新记录）
        private long mTextDataId;
        // 文本数据的差异值
        private ContentValues mTextDataValues;

        // 通话记录数据在 data 表中的 ID（0 表示待插入新记录）
        private long mCallDataId;
        // 通话记录数据的差异值
        private ContentValues mCallDataValues;

        private static final String TAG = "NoteData";

        /**
         * 构造方法，初始化文本和通话记录的数据容器，ID 设为 0 表示暂无记录。
         */
        public NoteData() {
            mTextDataValues = new ContentValues();
            mCallDataValues = new ContentValues();
            mTextDataId = 0;
            mCallDataId = 0;
        }

        /**
         * 判断是否有本地修改（文本或通话记录数据有变更）。
         *
         * @return true 表示有未保存的数据变更
         */
        boolean isLocalModified() {
            return mTextDataValues.size() > 0 || mCallDataValues.size() > 0;
        }

        /**
         * 设置文本数据的 ID，必须大于 0。
         *
         * @param id 文本数据记录 ID
         */
        void setTextDataId(long id) {
            if(id <= 0) {
                throw new IllegalArgumentException("Text data id should larger than 0");
            }
            mTextDataId = id;
        }

        /**
         * 设置通话记录数据的 ID，必须大于 0。
         *
         * @param id 通话记录数据 ID
         */
        void setCallDataId(long id) {
            if (id <= 0) {
                throw new IllegalArgumentException("Call data id should larger than 0");
            }
            mCallDataId = id;
        }

        /**
         * 设置通话记录数据的字段值，并更新笔记的修改标志和时间。
         *
         * @param key   字段名
         * @param value 字段值
         */
        void setCallData(String key, String value) {
            mCallDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        /**
         * 设置文本数据的字段值，并更新笔记的修改标志和时间。
         *
         * @param key   字段名
         * @param value 字段值
         */
        void setTextData(String key, String value) {
            mTextDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        /**
         * 将详细数据提交到 ContentProvider。
         * 对于新增数据（ID 为 0）执行 insert，对于已有数据执行 update。
         * 若同时存在文本和通话记录的更新，则使用批量操作（applyBatch）一次性提交。
         *
         * @param context 上下文
         * @param noteId  所属笔记的 ID
         * @return 成功返回笔记 URI，失败返回 null
         */
        Uri pushIntoContentResolver(Context context, long noteId) {
            // 安全性检查
            if (noteId <= 0) {
                throw new IllegalArgumentException("Wrong note id:" + noteId);
            }

            ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
            ContentProviderOperation.Builder builder = null;

            // 处理文本数据
            if(mTextDataValues.size() > 0) {
                mTextDataValues.put(DataColumns.NOTE_ID, noteId);
                if (mTextDataId == 0) {
                    // ID 为 0，说明是新数据，执行插入
                    mTextDataValues.put(DataColumns.MIME_TYPE, TextNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mTextDataValues);
                    try {
                        setTextDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new text data fail with noteId" + noteId);
                        mTextDataValues.clear();
                        return null;
                    }
                } else {
                    // ID 不为 0，说明已有记录，添加到批量更新列表
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mTextDataId));
                    builder.withValues(mTextDataValues);
                    operationList.add(builder.build());
                }
                mTextDataValues.clear();
            }

            // 处理通话记录数据
            if(mCallDataValues.size() > 0) {
                mCallDataValues.put(DataColumns.NOTE_ID, noteId);
                if (mCallDataId == 0) {
                    // ID 为 0，说明是新数据，执行插入
                    mCallDataValues.put(DataColumns.MIME_TYPE, CallNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mCallDataValues);
                    try {
                        setCallDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new call data fail with noteId" + noteId);
                        mCallDataValues.clear();
                        return null;
                    }
                } else {
                    // ID 不为 0，说明已有记录，添加到批量更新列表
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mCallDataId));
                    builder.withValues(mCallDataValues);
                    operationList.add(builder.build());
                }
                mCallDataValues.clear();
            }

            // 执行批量更新操作
            if (operationList.size() > 0) {
                try {
                    ContentProviderResult[] results = context.getContentResolver().applyBatch(
                            Notes.AUTHORITY, operationList);
                    return (results == null || results.length == 0 || results[0] == null) ? null
                            : ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                } catch (RemoteException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                } catch (OperationApplicationException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                }
            }
            return null;
        }
    }
}
