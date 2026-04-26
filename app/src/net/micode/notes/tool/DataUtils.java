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

// DataUtils：数据操作工具类，提供对便签数据库进行批量删除、移动、查询等静态方法
package net.micode.notes.tool;

import android.content.ContentProviderOperation;     // ContentProvider 批量操作
import android.content.ContentProviderResult;        // 批量操作结果
import android.content.ContentResolver;              // 内容解析器，用于访问 ContentProvider
import android.content.ContentUris;                  // 用于在 URI 后追加 ID
import android.content.ContentValues;                // 键值对容器，用于更新操作
import android.content.OperationApplicationException; // 批量操作应用异常
import android.database.Cursor;                      // 数据库游标
import android.os.RemoteException;                   // 远程异常
import android.util.Log;                             // 日志工具

import net.micode.notes.data.Notes;                  // 便签常量（URI、类型定义等）
import net.micode.notes.data.Notes.CallNote;         // 通话记录便签的字段常量
import net.micode.notes.data.Notes.NoteColumns;      // Note 表字段名常量
import net.micode.notes.ui.NotesListAdapter.AppWidgetAttribute; // 桌面小部件属性

import java.util.ArrayList;
import java.util.HashSet;

/**
 * DataUtils：便签数据操作工具类。
 * <p>
 * 该类中的所有方法均为静态方法，提供对便签和 Data 表的高层操作封装。
 * 包括：
 *   <ul>
 *     <li>批量删除便签（batchDeleteNotes）</li>
 *     <li>移动便签到文件夹（moveNoteToFoler / batchMoveToFolder）</li>
 *     <li>查询文件夹数量、检查便签/Data 是否存在</li>
 *     <li>获取桌面小部件信息、通话记录关联查询</li>
 *   </ul>
 * </p>
 * <p>
 * 所有方法均通过传入的 ContentResolver 来访问 ContentProvider，因此调用方
 * 需要持有合法的 Context 或 ContentResolver 引用。
 * </p>
 */
public class DataUtils {
    public static final String TAG = "DataUtils";

    /**
     * 批量删除便签。
     * <p>
     * 使用 ContentProviderOperation 进行批量操作，一次事务中删除多个便签。
     * 会跳过系统根文件夹（ID_ROOT_FOLDER），防止误删。
     * </p>
     *
     * @param resolver 内容解析器
     * @param ids      要删除的便签 ID 集合
     * @return true 表示删除成功，false 表示失败
     */
    public static boolean batchDeleteNotes(ContentResolver resolver, HashSet<Long> ids) {
        // 安全检查：ID 集合为空时直接返回成功（没有需要删除的）
        if (ids == null) {
            Log.d(TAG, "the ids is null");
            return true;
        }
        if (ids.size() == 0) {
            Log.d(TAG, "no id is in the hashset");
            return true;
        }

        // 构建批量操作列表
        ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
        for (long id : ids) {
            // 跳过系统根文件夹，不允许删除
            if(id == Notes.ID_ROOT_FOLDER) {
                Log.e(TAG, "Don't delete system folder root");
                continue;
            }
            // 为每个便签 ID 创建一条 DELETE 操作
            ContentProviderOperation.Builder builder = ContentProviderOperation
                    .newDelete(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id));
            operationList.add(builder.build());
        }

        try {
            // 通过 ContentResolver 一次性应用所有操作（在一个事务中）
            ContentProviderResult[] results = resolver.applyBatch(Notes.AUTHORITY, operationList);
            if (results == null || results.length == 0 || results[0] == null) {
                Log.d(TAG, "delete notes failed, ids:" + ids.toString());
                return false;
            }
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
        return false;
    }

    /**
     * 将单个便签移动到目标文件夹。
     * 同时更新 ORIGIN_PARENT_ID 字段，记录原文件夹 ID。
     *
     * @param resolver    内容解析器
     * @param id          便签 ID
     * @param srcFolderId 源文件夹 ID
     * @param desFolderId 目标文件夹 ID
     */
    public static void moveNoteToFoler(ContentResolver resolver, long id, long srcFolderId, long desFolderId) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.PARENT_ID, desFolderId);        // 设置新的父文件夹
        values.put(NoteColumns.ORIGIN_PARENT_ID, srcFolderId); // 记录原始父文件夹
        values.put(NoteColumns.LOCAL_MODIFIED, 1);             // 标记本地已修改（用于后续同步）
        resolver.update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id), values, null, null);
    }

    /**
     * 批量移动便签到指定文件夹。
     * <p>
     * 使用 ContentProviderOperation 批量更新，一次事务中完成所有移动操作。
     * </p>
     *
     * @param resolver 内容解析器
     * @param ids      要移动的便签 ID 集合
     * @param folderId 目标文件夹 ID
     * @return true 表示移动成功，false 表示失败
     */
    public static boolean batchMoveToFolder(ContentResolver resolver, HashSet<Long> ids,
                                            long folderId) {
        if (ids == null) {
            Log.d(TAG, "the ids is null");
            return true;
        }

        // 构建批量操作列表
        ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
        for (long id : ids) {
            ContentProviderOperation.Builder builder = ContentProviderOperation
                    .newUpdate(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id));
            builder.withValue(NoteColumns.PARENT_ID, folderId);   // 更新父文件夹
            builder.withValue(NoteColumns.LOCAL_MODIFIED, 1);     // 标记本地已修改
            operationList.add(builder.build());
        }

        try {
            ContentProviderResult[] results = resolver.applyBatch(Notes.AUTHORITY, operationList);
            if (results == null || results.length == 0 || results[0] == null) {
                Log.d(TAG, "delete notes failed, ids:" + ids.toString());
                return false;
            }
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
        return false;
    }

    /**
     * 获取用户创建的文件夹数量（不包括系统文件夹和回收站）。
     *
     * @param resolver 内容解析器
     * @return 用户文件夹数量
     */
    public static int getUserFolderCount(ContentResolver resolver) {
        Cursor cursor =resolver.query(Notes.CONTENT_NOTE_URI,
                new String[] { "COUNT(*)" },                                          // 只查询数量
                NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>?",        // 类型=文件夹且不在回收站中
                new String[] { String.valueOf(Notes.TYPE_FOLDER), String.valueOf(Notes.ID_TRASH_FOLER)},
                null);

        int count = 0;
        if(cursor != null) {
            if(cursor.moveToFirst()) {
                try {
                    count = cursor.getInt(0);   // 第一列即为 COUNT(*) 的结果
                } catch (IndexOutOfBoundsException e) {
                    Log.e(TAG, "get folder count failed:" + e.toString());
                } finally {
                    cursor.close();
                }
            }
        }
        return count;
    }

    /**
     * 检查指定便签在数据库中是否可见（指定类型且不在回收站中）。
     *
     * @param resolver 内容解析器
     * @param noteId   便签 ID
     * @param type     便签类型
     * @return true 表示可见，false 表示不可见（已删除或在回收站中）
     */
    public static boolean visibleInNoteDatabase(ContentResolver resolver, long noteId, int type) {
        Cursor cursor = resolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                null,
                NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER,
                new String [] {String.valueOf(type)},
                null);

        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 检查指定 ID 的便签是否存在于数据库中（不论类型和位置）。
     *
     * @param resolver 内容解析器
     * @param noteId   便签 ID
     * @return true 表示存在，false 表示不存在
     */
    public static boolean existInNoteDatabase(ContentResolver resolver, long noteId) {
        Cursor cursor = resolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                null, null, null, null);

        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 检查指定 ID 的 Data 记录是否存在于数据库中。
     *
     * @param resolver 内容解析器
     * @param dataId   Data 记录 ID
     * @return true 表示存在，false 表示不存在
     */
    public static boolean existInDataDatabase(ContentResolver resolver, long dataId) {
        Cursor cursor = resolver.query(ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId),
                null, null, null, null);

        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 检查是否存在同名且可见的文件夹。
     *
     * @param resolver 内容解析器
     * @param name     要检查的文件夹名称
     * @return true 表示已存在同名文件夹，false 表示不存在
     */
    public static boolean checkVisibleFolderName(ContentResolver resolver, String name) {
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI, null,
                NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER +
                        " AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER +
                        " AND " + NoteColumns.SNIPPET + "=?",
                new String[] { name }, null);
        boolean exist = false;
        if(cursor != null) {
            if(cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 获取指定文件夹下所有桌面小部件的属性（widgetId 和 widgetType）。
     *
     * @param resolver 内容解析器
     * @param folderId 文件夹 ID
     * @return AppWidgetAttribute 的集合，如果该文件夹下没有小部件则返回 null
     */
    public static HashSet<AppWidgetAttribute> getFolderNoteWidget(ContentResolver resolver, long folderId) {
        Cursor c = resolver.query(Notes.CONTENT_NOTE_URI,
                new String[] { NoteColumns.WIDGET_ID, NoteColumns.WIDGET_TYPE },
                NoteColumns.PARENT_ID + "=?",
                new String[] { String.valueOf(folderId) },
                null);

        HashSet<AppWidgetAttribute> set = null;
        if (c != null) {
            if (c.moveToFirst()) {
                set = new HashSet<AppWidgetAttribute>();
                do {
                    try {
                        AppWidgetAttribute widget = new AppWidgetAttribute();
                        widget.widgetId = c.getInt(0);     // 小部件 ID
                        widget.widgetType = c.getInt(1);   // 小部件类型
                        set.add(widget);
                    } catch (IndexOutOfBoundsException e) {
                        Log.e(TAG, e.toString());
                    }
                } while (c.moveToNext());
            }
            c.close();
        }
        return set;
    }

    /**
     * 根据便签 ID 获取通话记录中的电话号码。
     *
     * @param resolver 内容解析器
     * @param noteId   便签 ID
     * @return 电话号码字符串，若查询失败则返回空字符串 ""
     */
    public static String getCallNumberByNoteId(ContentResolver resolver, long noteId) {
        Cursor cursor = resolver.query(Notes.CONTENT_DATA_URI,
                new String [] { CallNote.PHONE_NUMBER },
                CallNote.NOTE_ID + "=? AND " + CallNote.MIME_TYPE + "=?",
                new String [] { String.valueOf(noteId), CallNote.CONTENT_ITEM_TYPE },
                null);

        if (cursor != null && cursor.moveToFirst()) {
            try {
                return cursor.getString(0);
            } catch (IndexOutOfBoundsException e) {
                Log.e(TAG, "Get call number fails " + e.toString());
            } finally {
                cursor.close();
            }
        }
        return "";
    }

    /**
     * 根据电话号码和通话日期查找对应的通话记录便签 ID。
     * 使用 PHONE_NUMBERS_EQUAL 函数进行电话号码的模糊匹配，以兼容不同格式。
     * @param resolver     内容解析器
     * @param phoneNumber  电话号码
     * @param callDate     通话日期（时间戳）
     * @return 匹配的便签 ID，若未找到则返回 0
     */
    public static long getNoteIdByPhoneNumberAndCallDate(ContentResolver resolver, String phoneNumber, long callDate) {
        Cursor cursor = resolver.query(Notes.CONTENT_DATA_URI,
                new String [] { CallNote.NOTE_ID },
                CallNote.CALL_DATE + "=? AND " + CallNote.MIME_TYPE + "=? AND PHONE_NUMBERS_EQUAL("
                        + CallNote.PHONE_NUMBER + ",?)",
                new String [] { String.valueOf(callDate), CallNote.CONTENT_ITEM_TYPE, phoneNumber },
                null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                try {
                    return cursor.getLong(0);
                } catch (IndexOutOfBoundsException e) {
                    Log.e(TAG, "Get call note id fails " + e.toString());
                }
            }
            cursor.close();
        }
        return 0;
    }

    /**
     * 根据便签 ID 获取内容摘要（SNIPPET 字段）。
     *
     * @param resolver 内容解析器
     * @param noteId   便签 ID
     * @return 内容摘要字符串，若便签不存在则返回空字符串 ""
     * @throws IllegalArgumentException 如果指定 ID 的便签不存在
     */
    public static String getSnippetById(ContentResolver resolver, long noteId) {
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI,
                new String [] { NoteColumns.SNIPPET },
                NoteColumns.ID + "=?",
                new String [] { String.valueOf(noteId)},
                null);

        if (cursor != null) {
            String snippet = "";
            if (cursor.moveToFirst()) {
                snippet = cursor.getString(0);
            }
            cursor.close();
            return snippet;
        }
        throw new IllegalArgumentException("Note is not found with id: " + noteId);
    }

    /**
     * 格式化便签摘要：去除首尾空白，只保留第一行内容（以换行符分隔）。
     *
     * @param snippet 原始摘要内容
     * @return 格式化后的摘要，若原始为 null 则返回 null
     */
    public static String getFormattedSnippet(String snippet) {
        if (snippet != null) {
            snippet = snippet.trim();                // 去除首尾空白
            int index = snippet.indexOf('\n');       // 查找第一个换行符
            if (index != -1) {
                snippet = snippet.substring(0, index); // 截取第一行
            }
        }
        return snippet;
    }
}