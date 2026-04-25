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

package net.micode.notes.data;


import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;

/**
 * 便签应用的内容提供器（ContentProvider）。
 * 提供对笔记和数据的增删改查统一接口，支持搜索建议，并自动维护版本号。
 */
public class NotesProvider extends ContentProvider {
    /**
     * URI 匹配器，用于分发不同的 URI 请求
     */
    private static final UriMatcher mMatcher;

    /**
     * 数据库辅助类实例
     */
    private NotesDatabaseHelper mHelper;

    private static final String TAG = "NotesProvider";

    // URI 匹配码：笔记列表
    private static final int URI_NOTE            = 1;
    // URI 匹配码：单个笔记
    private static final int URI_NOTE_ITEM       = 2;
    // URI 匹配码：数据列表
    private static final int URI_DATA            = 3;
    // URI 匹配码：单个数据
    private static final int URI_DATA_ITEM       = 4;
    // URI 匹配码：搜索
    private static final int URI_SEARCH          = 5;
    // URI 匹配码：搜索建议
    private static final int URI_SEARCH_SUGGEST  = 6;

    // 在 NotesProvider 类中添加常量
    private static final int URI_TAG                = 7;
    private static final int URI_TAG_ITEM           = 8;
    private static final int URI_NOTE_TAG           = 9;
    private static final int URI_NOTE_TAG_ITEM      = 10;
    private static final int URI_NOTE_TAG_BY_NOTE   = 11;   // 根据笔记ID查关联
    private static final int URI_NOTE_BY_TAG        = 12;   // 根据标签ID查笔记（多表联查）

    static {
        mMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        // 注册 URI 模式
        mMatcher.addURI(Notes.AUTHORITY, "note", URI_NOTE);
        mMatcher.addURI(Notes.AUTHORITY, "note/#", URI_NOTE_ITEM);
        mMatcher.addURI(Notes.AUTHORITY, "data", URI_DATA);
        mMatcher.addURI(Notes.AUTHORITY, "data/#", URI_DATA_ITEM);
        mMatcher.addURI(Notes.AUTHORITY, "search", URI_SEARCH);
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, URI_SEARCH_SUGGEST);
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY + "/*", URI_SEARCH_SUGGEST);

        mMatcher.addURI(Notes.AUTHORITY, "tag", URI_TAG);
        mMatcher.addURI(Notes.AUTHORITY, "tag/#", URI_TAG_ITEM);
        mMatcher.addURI(Notes.AUTHORITY, "note_tag", URI_NOTE_TAG);
        mMatcher.addURI(Notes.AUTHORITY, "note_tag/#", URI_NOTE_TAG_ITEM);
        mMatcher.addURI(Notes.AUTHORITY, "note_tag/note/#", URI_NOTE_TAG_BY_NOTE);
        mMatcher.addURI(Notes.AUTHORITY, "note/by_tag/#", URI_NOTE_BY_TAG);
    }

    /**
     * 搜索结果的投影字段。
     * 将 snippet 中的换行符（x'0A' 即 '\n'）替换后裁剪，以便在搜索结果中显示更多信息。
     */
    private static final String NOTES_SEARCH_PROJECTION = NoteColumns.ID + ","
        + NoteColumns.ID + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA + ","
        + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_1 + ","
        + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_2 + ","
        + R.drawable.search_result + " AS " + SearchManager.SUGGEST_COLUMN_ICON_1 + ","
        + "'" + Intent.ACTION_VIEW + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_ACTION + ","
        + "'" + Notes.TextNote.CONTENT_TYPE + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA;

    /**
     * 搜索笔记 snippet 的 SQL 查询语句。
     * 排除回收站中的笔记，仅查询普通笔记。
     */
    private static String NOTES_SNIPPET_SEARCH_QUERY = "SELECT " + NOTES_SEARCH_PROJECTION
        + " FROM " + TABLE.NOTE
        + " WHERE " + NoteColumns.SNIPPET + " LIKE ?"
        + " AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER
        + " AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE;

    /**
     * ContentProvider 初始化时获取数据库辅助类单例。
     * @return true 表示初始化成功
     */
    @Override
    public boolean onCreate() {
        mHelper = NotesDatabaseHelper.getInstance(getContext());
        return true;
    }

    /**
     * 查询操作。
     * 根据 URI 匹配进入不同的查询逻辑：笔记/数据/搜索。
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        Cursor c = null;
        SQLiteDatabase db = mHelper.getReadableDatabase();
        String id = null;
        switch (mMatcher.match(uri)) {
            case URI_TAG:
                c = db.query("tag", projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case URI_TAG_ITEM:
                id = uri.getPathSegments().get(1);
                c = db.query("tag", projection, "(" + Notes.TagColumns.ID + "=?)" + parseSelection(selection),
                        new String[]{id}, null, null, sortOrder);
                break;
            case URI_NOTE_TAG:
                c = db.query("note_tag", projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case URI_NOTE_TAG_BY_NOTE:
                id = uri.getPathSegments().get(2);  // note_tag/note/123
                c = db.query("note_tag", projection, "(" + Notes.NoteTagColumns.NOTE_ID + "=?)" + parseSelection(selection),
                        new String[]{id}, null, null, sortOrder);
                break;
            case URI_NOTE_BY_TAG:
                // 多表联查：根据标签ID查询笔记列表
                id = uri.getPathSegments().get(2);  // note/by_tag/456
                // 使用 rawQuery 或 db.query 联查
                String sql = "SELECT n.* FROM " + TABLE.NOTE + " n INNER JOIN note_tag nt ON n." + NoteColumns.ID + "=nt." + Notes.NoteTagColumns.NOTE_ID +
                        " WHERE nt." + Notes.NoteTagColumns.TAG_ID + "=? AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE;
                c = db.rawQuery(sql, new String[]{id});
                break;
            case URI_NOTE:
                // 查询所有笔记/文件夹
                c = db.query(TABLE.NOTE, projection, selection, selectionArgs, null, null,
                        sortOrder);
                break;
            case URI_NOTE_ITEM:
                // 根据 ID 查询单个笔记
                id = uri.getPathSegments().get(1);
                c = db.query(TABLE.NOTE, projection, NoteColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs, null, null, sortOrder);
                break;
            case URI_DATA:
                // 查询所有数据记录
                c = db.query(TABLE.DATA, projection, selection, selectionArgs, null, null,
                        sortOrder);
                break;
            case URI_DATA_ITEM:
                // 根据 ID 查询单条数据
                id = uri.getPathSegments().get(1);
                c = db.query(TABLE.DATA, projection, DataColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs, null, null, sortOrder);
                break;
            case URI_SEARCH:
            case URI_SEARCH_SUGGEST:
                // 搜索或搜索建议，不允许指定排序、投影等额外参数
                if (sortOrder != null || projection != null) {
                    throw new IllegalArgumentException(
                            "do not specify sortOrder, selection, selectionArgs, or projection" + "with this query");
                }

                String searchString = null;
                if (mMatcher.match(uri) == URI_SEARCH_SUGGEST) {
                    if (uri.getPathSegments().size() > 1) {
                        searchString = uri.getPathSegments().get(1);
                    }
                } else {
                    searchString = uri.getQueryParameter("pattern");
                }

                if (TextUtils.isEmpty(searchString)) {
                    return null;
                }

                try {
                    // 拼接通配符进行模糊搜索
                    searchString = String.format("%%%s%%", searchString);
                    c = db.rawQuery(NOTES_SNIPPET_SEARCH_QUERY,
                            new String[] { searchString });
                } catch (IllegalStateException ex) {
                    Log.e(TAG, "got exception: " + ex.toString());
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        if (c != null) {
            // 设置游标变化通知 URI，以便数据变更时自动刷新
            c.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return c;
    }

    /**
     * 插入操作。
     * 支持插入笔记或数据记录，插入成功后通知相关 URI 数据变化。
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = mHelper.getWritableDatabase();
        long dataId = 0, noteId = 0, insertedId = 0;
        switch (mMatcher.match(uri)) {
            case URI_TAG:
                // 插入新标签，如果已有同名标签则忽略或返回已存在ID
                String name = values.getAsString(Notes.TagColumns.NAME);
                // 查询是否已存在
                Cursor tagCursor = db.query("tag", new String[]{Notes.TagColumns.ID},
                        Notes.TagColumns.NAME + "=?", new String[]{name}, null, null, null);
                if (tagCursor != null && tagCursor.moveToFirst()) {
                    insertedId = tagCursor.getLong(0);
                } else {
                    insertedId = db.insert("tag", null, values);
                }
                if (tagCursor != null) tagCursor.close();
                break;
            case URI_NOTE_TAG:
                insertedId = db.insert("note_tag", null, values);
                break;
            case URI_NOTE:
                insertedId = noteId = db.insert(TABLE.NOTE, null, values);
                break;
            case URI_DATA:
                if (values.containsKey(DataColumns.NOTE_ID)) {
                    noteId = values.getAsLong(DataColumns.NOTE_ID);
                } else {
                    Log.d(TAG, "Wrong data format without note id:" + values.toString());
                }
                insertedId = dataId = db.insert(TABLE.DATA, null, values);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        // 通知笔记 URI 数据变化
        if (noteId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), null);
        }
        // 通知数据 URI 数据变化
        if (dataId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId), null);
        }

        return ContentUris.withAppendedId(uri, insertedId);
    }

    /**
     * 删除操作。
     * 系统文件夹（ID <= 0）不允许删除。删除数据记录时会同时通知笔记 URI 更新。
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int count = 0;
        String id = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean deleteData = false;
        switch (mMatcher.match(uri)) {
            case URI_NOTE_TAG:
                count = db.delete("note_tag", selection, selectionArgs);
                break;
            case URI_TAG_ITEM:
                id = uri.getPathSegments().get(1);
                count = db.delete("tag", Notes.TagColumns.ID + "=?" + parseSelection(selection), new String[]{id});
                break;
            case URI_NOTE_TAG_ITEM:
                id = uri.getPathSegments().get(1);
                count = db.delete("note_tag", Notes.NoteTagColumns.ID + "=?" + parseSelection(selection), new String[]{id});
                break;
            // 删除笔记时自动删除关联记录，已通过外键级联删除，无需额外处理
            case URI_NOTE:
                // 保护系统文件夹不被批量删除
                selection = "(" + selection + ") AND " + NoteColumns.ID + ">0 ";
                count = db.delete(TABLE.NOTE, selection, selectionArgs);
                break;
            case URI_NOTE_ITEM:
                id = uri.getPathSegments().get(1);
                // ID 小于等于 0 的是系统文件夹，不允许删除
                long noteId = Long.valueOf(id);
                if (noteId <= 0) {
                    break;
                }
                count = db.delete(TABLE.NOTE,
                        NoteColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                break;
            case URI_DATA:
                count = db.delete(TABLE.DATA, selection, selectionArgs);
                deleteData = true;
                break;
            case URI_DATA_ITEM:
                id = uri.getPathSegments().get(1);
                count = db.delete(TABLE.DATA,
                        DataColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                deleteData = true;
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        if (count > 0) {
            if (deleteData) {
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    /**
     * 更新操作。
     * 更新笔记或数据记录。更新笔记前会自动增加其版本号。更新数据记录时会通知笔记 URI 刷新。
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int count = 0;
        String id = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean updateData = false;
        switch (mMatcher.match(uri)) {
            case URI_TAG_ITEM:
                id = uri.getPathSegments().get(1);
                count = db.update("tag", values, Notes.TagColumns.ID + "=?" + parseSelection(selection), new String[]{id});
                break;
            case URI_NOTE:
                increaseNoteVersion(-1, selection, selectionArgs);
                count = db.update(TABLE.NOTE, values, selection, selectionArgs);
                break;
            case URI_NOTE_ITEM:
                id = uri.getPathSegments().get(1);
                increaseNoteVersion(Long.valueOf(id), selection, selectionArgs);
                count = db.update(TABLE.NOTE, values, NoteColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs);
                break;
            case URI_DATA:
                count = db.update(TABLE.DATA, values, selection, selectionArgs);
                updateData = true;
                break;
            case URI_DATA_ITEM:
                id = uri.getPathSegments().get(1);
                count = db.update(TABLE.DATA, values, DataColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs);
                updateData = true;
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        if (count > 0) {
            if (updateData) {
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    /**
     * 工具方法：如果 selection 不为空，则在其前面添加 " AND ("，后面补 ")"，
     * 用于拼接到现有的 WHERE 条件后。
     */
    private String parseSelection(String selection) {
        return (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : "");
    }

    /**
     * 增加笔记的版本号。
     * 构建 UPDATE 语句将指定笔记的 version 字段加 1，用于乐观锁机制。
     *
     * @param id            笔记 ID，如果 <= 0 则不限制 ID
     * @param selection     额外的筛选条件
     * @param selectionArgs 参数
     */
    private void increaseNoteVersion(long id, String selection, String[] selectionArgs) {
        StringBuilder sql = new StringBuilder(120);
        sql.append("UPDATE ");
        sql.append(TABLE.NOTE);
        sql.append(" SET ");
        sql.append(NoteColumns.VERSION);
        sql.append("=" + NoteColumns.VERSION + "+1 ");

        if (id > 0 || !TextUtils.isEmpty(selection)) {
            sql.append(" WHERE ");
        }
        if (id > 0) {
            sql.append(NoteColumns.ID + "=" + String.valueOf(id));
        }
        if (!TextUtils.isEmpty(selection)) {
            String selectString = id > 0 ? parseSelection(selection) : selection;
            // 将 selectionArgs 中的参数替换到 selection 中（注意：此处可能存在 SQL 注入风险，原代码如此）
            for (String args : selectionArgs) {
                selectString = selectString.replaceFirst("\\?", args);
            }
            sql.append(selectString);
        }

        mHelper.getWritableDatabase().execSQL(sql.toString()); // 直接执行原生 SQL
    }

    /**
     * 返回 MIME 类型，目前未实现，返回 null。
     */
    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }
}
