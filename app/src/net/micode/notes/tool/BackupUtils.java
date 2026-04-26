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

// BackupUtils：单例工具类，负责将本地全部便签导出为可读的文本文件保存到 SD 卡
package net.micode.notes.tool;

import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

/**
 * BackupUtils：便签文本备份工具。
 * <p>
 * 以单例模式提供统一的备份入口，将本地便签按文件夹和便签二级结构
 * 导出到 SD 卡上的 txt 文件中，方便用户离线查看或迁移数据。
 * </p>
 * <p>
 * 导出过程由内部类 TextExport 完成，BackupUtils 本身负责状态管理和
 * 对外接口。
 * </p>
 */
public class BackupUtils {
    private static final String TAG = "BackupUtils";

    // ==================== 单例 ====================
    // 静态单例实例
    private static BackupUtils sInstance;

    /**
     * 获取 BackupUtils 的单例对象，双重检查锁定保证线程安全。
     *
     * @param context 上下文，用于初始化 TextExport
     * @return BackupUtils 唯一实例
     */
    public static synchronized BackupUtils getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new BackupUtils(context);
        }
        return sInstance;
    }

    // ==================== 状态常量 ====================
    /**
     * 以下常量用于表示备份或还原操作的结果状态。
     */
    // SD 卡未挂载
    public static final int STATE_SD_CARD_UNMOUONTED           = 0;
    // 备份文件不存在
    public static final int STATE_BACKUP_FILE_NOT_EXIST        = 1;
    // 数据格式被破坏，可能被其他程序修改过
    public static final int STATE_DATA_DESTROIED               = 2;
    // 发生运行时异常导致备份或还原失败
    public static final int STATE_SYSTEM_ERROR                 = 3;
    // 备份或还原成功
    public static final int STATE_SUCCESS                      = 4;

    // 实际执行导出的内部类实例
    private TextExport mTextExport;

    // 私有构造方法，防止外部直接实例化
    private BackupUtils(Context context) {
        mTextExport = new TextExport(context);
    }

    // ==================== 外部可用方法 ====================

    /**
     * 判断外部存储（SD 卡）是否可读写。
     *
     * @return true 表示 SD 卡已挂载且可读写；false 则不可用
     */
    private static boolean externalStorageAvailable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    /**
     * 执行文本导出操作，将所有便签导出为文本文件保存到 SD 卡。
     *
     * @return 导出状态码，如 STATE_SUCCESS、STATE_SD_CARD_UNMOUONTED 等
     */
    public int exportToText() {
        return mTextExport.exportToText();
    }

    /**
     * 获取导出文件的名称（不含路径）。
     *
     * @return 导出文件名
     */
    public String getExportedTextFileName() {
        return mTextExport.mFileName;
    }

    /**
     * 获取导出文件所在的目录。
     *
     * @return 导出文件目录路径
     */
    public String getExportedTextFileDir() {
        return mTextExport.mFileDirectory;
    }

    // ==================== 内部类：TextExport ====================

    /**
     * TextExport：内部类，负责实际的文本导出逻辑。
     * <p>
     * 遍历本地数据库中的文件夹和便签，将其格式化为用户可读的文本内容，
     * 按顺序写入 SD 卡上的 .txt 文件。
     * </p>
     */
    private static class TextExport {
        // 查询便签表需要的字段
        private static final String[] NOTE_PROJECTION = {
                NoteColumns.ID,
                NoteColumns.MODIFIED_DATE,
                NoteColumns.SNIPPET,
                NoteColumns.TYPE
        };

        private static final int NOTE_COLUMN_ID = 0;                // 便签 ID 列索引
        private static final int NOTE_COLUMN_MODIFIED_DATE = 1;     // 修改日期列索引
        private static final int NOTE_COLUMN_SNIPPET = 2;           // 摘要/文件夹名列索引

        // 查询 Data 表需要的字段
        private static final String[] DATA_PROJECTION = {
                DataColumns.CONTENT,
                DataColumns.MIME_TYPE,
                DataColumns.DATA1,
                DataColumns.DATA2,
                DataColumns.DATA3,
                DataColumns.DATA4,
        };

        private static final int DATA_COLUMN_CONTENT = 0;           // 内容列索引
        private static final int DATA_COLUMN_MIME_TYPE = 1;         // MIME 类型列索引
        private static final int DATA_COLUMN_CALL_DATE = 2;         // 通话日期（用于通话记录）列索引
        private static final int DATA_COLUMN_PHONE_NUMBER = 4;      // 电话号码列索引

        // 文本格式化模板，从资源文件中加载，包含文件夹名、便签日期、便签内容三种格式
        private final String [] TEXT_FORMAT;
        private static final int FORMAT_FOLDER_NAME          = 0;   // 文件夹名称格式
        private static final int FORMAT_NOTE_DATE            = 1;   // 便签日期格式
        private static final int FORMAT_NOTE_CONTENT         = 2;   // 便签内容格式

        private Context mContext;
        private String mFileName;      // 导出的文件名
        private String mFileDirectory; // 导出文件所在目录

        /**
         * 构造方法：初始化格式化模板、上下文以及默认文件名和目录。
         *
         * @param context 上下文
         */
        public TextExport(Context context) {
            // 从资源文件获取格式化模板数组
            TEXT_FORMAT = context.getResources().getStringArray(R.array.format_for_exported_note);
            mContext = context;
            mFileName = "";
            mFileDirectory = "";
        }

        /**
         * 根据 ID 获取格式化字符串。
         *
         * @param id 格式化模板索引
         * @return 格式化字符串
         */
        private String getFormat(int id) {
            return TEXT_FORMAT[id];
        }

        /**
         * 将指定文件夹下的所有便签导出到文本流。
         * 先打印每个便签的修改日期，再调用 exportNoteToText 输出其内容。
         *
         * @param folderId 文件夹 ID
         * @param ps       输出流
         */
        private void exportFolderToText(String folderId, PrintStream ps) {
            // 查询该文件夹下的所有便签
            Cursor notesCursor = mContext.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION, NoteColumns.PARENT_ID + "=?", new String[] {
                            folderId
                    }, null);

            if (notesCursor != null) {
                if (notesCursor.moveToFirst()) {
                    do {
                        // 打印便签的修改日期，格式如 "05/20 14:30"
                        ps.println(String.format(getFormat(FORMAT_NOTE_DATE), DateFormat.format(
                                mContext.getString(R.string.format_datetime_mdhm),
                                notesCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));
                        // 查询该便签下的数据内容
                        String noteId = notesCursor.getString(NOTE_COLUMN_ID);
                        exportNoteToText(noteId, ps);
                    } while (notesCursor.moveToNext());
                }
                notesCursor.close();
            }
        }

        /**
         * 导出指定便签的内容到文本流。
         * 根据 MIME 类型区分普通便签和通话记录，分别处理输出格式。
         *
         * @param noteId 便签 ID
         * @param ps     输出流
         */
        private void exportNoteToText(String noteId, PrintStream ps) {
            Cursor dataCursor = mContext.getContentResolver().query(Notes.CONTENT_DATA_URI,
                    DATA_PROJECTION, DataColumns.NOTE_ID + "=?", new String[] {
                            noteId
                    }, null);

            if (dataCursor != null) {
                if (dataCursor.moveToFirst()) {
                    do {
                        String mimeType = dataCursor.getString(DATA_COLUMN_MIME_TYPE);
                        if (DataConstants.CALL_NOTE.equals(mimeType)) {
                            // 处理通话记录便签
                            String phoneNumber = dataCursor.getString(DATA_COLUMN_PHONE_NUMBER);
                            long callDate = dataCursor.getLong(DATA_COLUMN_CALL_DATE);
                            String location = dataCursor.getString(DATA_COLUMN_CONTENT);

                            // 输出电话号码
                            if (!TextUtils.isEmpty(phoneNumber)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        phoneNumber));
                            }
                            // 输出通话日期
                            ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT), DateFormat
                                    .format(mContext.getString(R.string.format_datetime_mdhm),
                                            callDate)));
                            // 输出通话录音附件位置
                            if (!TextUtils.isEmpty(location)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        location));
                            }
                        } else if (DataConstants.NOTE.equals(mimeType)) {
                            // 处理普通文本便签
                            String content = dataCursor.getString(DATA_COLUMN_CONTENT);
                            if (!TextUtils.isEmpty(content)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        content));
                            }
                        }
                    } while (dataCursor.moveToNext());
                }
                dataCursor.close();
            }
            // 输出一个换行分隔符，分隔不同便签
            try {
                ps.write(new byte[] {
                        Character.LINE_SEPARATOR, Character.LETTER_NUMBER
                });
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }

        /**
         * 执行完整的文本导出流程。
         * <p>
         * 步骤：
         * <ol>
         *   <li>检查 SD 卡是否可用</li>
         *   <li>创建输出文件并获取 PrintStream</li>
         *   <li>先导出所有文件夹及其下的便签</li>
         *   <li>再导出根目录下无文件夹归属的便签</li>
         *   <li>关闭流并返回成功状态</li>
         * </ol>
         *
         * @return 导出状态码
         */
        public int exportToText() {
            if (!externalStorageAvailable()) {
                Log.d(TAG, "Media was not mounted");
                return STATE_SD_CARD_UNMOUONTED;
            }

            PrintStream ps = getExportToTextPrintStream();
            if (ps == null) {
                Log.e(TAG, "get print stream error");
                return STATE_SYSTEM_ERROR;
            }

            // 第一步：导出所有文件夹（不包括回收站）及其便签
            Cursor folderCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION,
                    "(" + NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER + " AND "
                            + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER + ") OR "
                            + NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER, null, null);

            if (folderCursor != null) {
                if (folderCursor.moveToFirst()) {
                    do {
                        // 获取文件夹名称
                        String folderName = "";
                        if(folderCursor.getLong(NOTE_COLUMN_ID) == Notes.ID_CALL_RECORD_FOLDER) {
                            folderName = mContext.getString(R.string.call_record_folder_name);
                        } else {
                            folderName = folderCursor.getString(NOTE_COLUMN_SNIPPET);
                        }
                        if (!TextUtils.isEmpty(folderName)) {
                            // 按照格式化模板输出文件夹名称
                            ps.println(String.format(getFormat(FORMAT_FOLDER_NAME), folderName));
                        }
                        String folderId = folderCursor.getString(NOTE_COLUMN_ID);
                        exportFolderToText(folderId, ps);
                    } while (folderCursor.moveToNext());
                }
                folderCursor.close();
            }

            // 第二步：导出根目录下不属于任何文件夹的便签
            Cursor noteCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION,
                    NoteColumns.TYPE + "=" + +Notes.TYPE_NOTE + " AND " + NoteColumns.PARENT_ID
                            + "=0", null, null);

            if (noteCursor != null) {
                if (noteCursor.moveToFirst()) {
                    do {
                        ps.println(String.format(getFormat(FORMAT_NOTE_DATE), DateFormat.format(
                                mContext.getString(R.string.format_datetime_mdhm),
                                noteCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));
                        String noteId = noteCursor.getString(NOTE_COLUMN_ID);
                        exportNoteToText(noteId, ps);
                    } while (noteCursor.moveToNext());
                }
                noteCursor.close();
            }
            ps.close();

            return STATE_SUCCESS;
        }

        /**
         * 创建指向 SD 卡上导出文件的 PrintStream。
         * 文件名基于当前日期生成，避免覆盖历史备份。
         *
         * @return PrintStream 对象，失败返回 null
         */
        private PrintStream getExportToTextPrintStream() {
            File file = generateFileMountedOnSDcard(mContext, R.string.file_path,
                    R.string.file_name_txt_format);
            if (file == null) {
                Log.e(TAG, "create file to exported failed");
                return null;
            }
            mFileName = file.getName();
            mFileDirectory = mContext.getString(R.string.file_path);
            PrintStream ps = null;
            try {
                FileOutputStream fos = new FileOutputStream(file);
                ps = new PrintStream(fos);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return null;
            } catch (NullPointerException e) {
                e.printStackTrace();
                return null;
            }
            return ps;
        }
    }

    /**
     * 在 SD 卡指定目录下生成文本文件，用于存放导出的便签数据。
     * 文件名带有日期标识，格式如 "note_20260520.txt"。
     *
     * @param context              上下文
     * @param filePathResId        文件路径资源 ID（如 "/Notes/"）
     * @param fileNameFormatResId  文件名格式资源 ID
     * @return 生成的 File 对象，若创建失败返回 null
     */
    private static File generateFileMountedOnSDcard(Context context, int filePathResId, int fileNameFormatResId) {
        StringBuilder sb = new StringBuilder();
        sb.append(Environment.getExternalStorageDirectory());
        sb.append(context.getString(filePathResId));
        File filedir = new File(sb.toString());
        // 文件名中包含日期，如 note_20260520.txt
        sb.append(context.getString(
                fileNameFormatResId,
                DateFormat.format(context.getString(R.string.format_date_ymd),
                        System.currentTimeMillis())));
        File file = new File(sb.toString());

        try {
            if (!filedir.exists()) {
                filedir.mkdir();
            }
            if (!file.exists()) {
                file.createNewFile();
            }
            return file;
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}