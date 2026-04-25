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

import android.net.Uri;

/**
 * 便签应用数据层常量定义类。
 * 包含权限、URI、表字段、笔记类型、系统文件夹ID、Intent附加键等全局常量。
 */
public class Notes {
    // 内容提供者的授权标识
    public static final String AUTHORITY = "micode_notes";
    // 日志标签
    public static final String TAG = "Notes";
    // 笔记类型：普通笔记
    public static final int TYPE_NOTE     = 0;
    // 笔记类型：文件夹
    public static final int TYPE_FOLDER   = 1;
    // 笔记类型：系统文件夹
    public static final int TYPE_SYSTEM   = 2;

    /**
     * 以下ID为系统文件夹的标识
     * ID_ROOT_FOLDER: 根文件夹（默认）
     * ID_TEMPARAY_FOLDER: 临时文件夹（不属于任何文件夹的笔记放置在此）
     * ID_CALL_RECORD_FOLDER: 通话记录文件夹
     * ID_TRASH_FOLER: 回收站文件夹
     */
    public static final int ID_ROOT_FOLDER = 0;
    public static final int ID_TEMPARAY_FOLDER = -1;
    public static final int ID_CALL_RECORD_FOLDER = -2;
    public static final int ID_TRASH_FOLER = -3;

    // Intent 附加信息键：提醒时间
    public static final String INTENT_EXTRA_ALERT_DATE = "net.micode.notes.alert_date";
    // Intent 附加信息键：背景色ID
    public static final String INTENT_EXTRA_BACKGROUND_ID = "net.micode.notes.background_color_id";
    // Intent 附加信息键：桌面小部件ID
    public static final String INTENT_EXTRA_WIDGET_ID = "net.micode.notes.widget_id";
    // Intent 附加信息键：桌面小部件类型
    public static final String INTENT_EXTRA_WIDGET_TYPE = "net.micode.notes.widget_type";
    // Intent 附加信息键：文件夹ID
    public static final String INTENT_EXTRA_FOLDER_ID = "net.micode.notes.folder_id";
    // Intent 附加信息键：通话日期
    public static final String INTENT_EXTRA_CALL_DATE = "net.micode.notes.call_date";

    // 无效小部件类型
    public static final int TYPE_WIDGET_INVALIDE      = -1;
    // 2x2 桌面小部件
    public static final int TYPE_WIDGET_2X            = 0;
    // 4x2 桌面小部件
    public static final int TYPE_WIDGET_4X            = 1;

    // 数据内容类型常量
    public static class DataConstants {
        // 文本笔记的内容类型
        public static final String NOTE = TextNote.CONTENT_ITEM_TYPE;
        // 通话记录笔记的内容类型
        public static final String CALL_NOTE = CallNote.CONTENT_ITEM_TYPE;
    }

    /**
     * 查询所有笔记和文件夹的 Uri
     */
    public static final Uri CONTENT_NOTE_URI = Uri.parse("content://" + AUTHORITY + "/note");

    /**
     * 查询数据表的 Uri
     */
    public static final Uri CONTENT_DATA_URI = Uri.parse("content://" + AUTHORITY + "/data");

    /**
     * 笔记表字段定义
     */
    public interface NoteColumns {
        /**
         * 主键ID
         * 类型: INTEGER (long)
         */
        public static final String ID = "_id";

        /**
         * 父文件夹ID（笔记所属文件夹）
         * 类型: INTEGER (long)
         */
        public static final String PARENT_ID = "parent_id";

        /**
         * 创建时间
         * 类型: INTEGER (long)
         */
        public static final String CREATED_DATE = "created_date";

        /**
         * 最后修改时间
         * 类型: INTEGER (long)
         */
        public static final String MODIFIED_DATE = "modified_date";

        /**
         * 提醒时间
         * 类型: INTEGER (long)
         */
        public static final String ALERTED_DATE = "alert_date";

        /**
         * 摘要内容（文件夹名称或笔记的文本预览）
         * 类型: TEXT
         */
        public static final String SNIPPET = "snippet";

        /**
         * 关联的桌面小部件ID
         * 类型: INTEGER (long)
         */
        public static final String WIDGET_ID = "widget_id";

        /**
         * 桌面小部件类型
         * 类型: INTEGER (long)
         */
        public static final String WIDGET_TYPE = "widget_type";

        /**
         * 背景颜色ID
         * 类型: INTEGER (long)
         */
        public static final String BG_COLOR_ID = "bg_color_id";

        /**
         * 是否含有附件（0:无 1:有）
         * 类型: INTEGER
         */
        public static final String HAS_ATTACHMENT = "has_attachment";

        /**
         * 文件夹内的笔记数量
         * 类型: INTEGER (long)
         */
        public static final String NOTES_COUNT = "notes_count";

        /**
         * 类型：文件夹或笔记
         * 类型: INTEGER
         */
        public static final String TYPE = "type";

        /**
         * 上次同步ID
         * 类型: INTEGER (long)
         */
        public static final String SYNC_ID = "sync_id";

        /**
         * 本地是否已修改（用于同步判断）
         * 类型: INTEGER
         */
        public static final String LOCAL_MODIFIED = "local_modified";

        /**
         * 移入临时文件夹前的原始父文件夹ID
         * 类型: INTEGER
         */
        public static final String ORIGIN_PARENT_ID = "origin_parent_id";

        /**
         * Google Task ID（同步用）
         * 类型: TEXT
         */
        public static final String GTASK_ID = "gtask_id";

        /**
         * 数据版本号（乐观锁）
         * 类型: INTEGER (long)
         */
        public static final String VERSION = "version";
    }

    /**
     * 数据表字段定义
     */
    public interface DataColumns {
        /**
         * 主键ID
         * 类型: INTEGER (long)
         */
        public static final String ID = "_id";

        /**
         * MIME 类型，表示该行数据的类型
         * 类型: TEXT
         */
        public static final String MIME_TYPE = "mime_type";

        /**
         * 关联的笔记ID
         * 类型: INTEGER (long)
         */
        public static final String NOTE_ID = "note_id";

        /**
         * 创建时间
         * 类型: INTEGER (long)
         */
        public static final String CREATED_DATE = "created_date";

        /**
         * 最后修改时间
         * 类型: INTEGER (long)
         */
        public static final String MODIFIED_DATE = "modified_date";

        /**
         * 数据内容
         * 类型: TEXT
         */
        public static final String CONTENT = "content";

        /**
         * 通用整型数据列，具体含义取决于 MIME_TYPE
         * 类型: INTEGER
         */
        public static final String DATA1 = "data1";

        /**
         * 通用整型数据列，具体含义取决于 MIME_TYPE
         * 类型: INTEGER
         */
        public static final String DATA2 = "data2";

        /**
         * 通用文本数据列，具体含义取决于 MIME_TYPE
         * 类型: TEXT
         */
        public static final String DATA3 = "data3";

        /**
         * 通用文本数据列，具体含义取决于 MIME_TYPE
         * 类型: TEXT
         */
        public static final String DATA4 = "data4";

        /**
         * 通用文本数据列，具体含义取决于 MIME_TYPE
         * 类型: TEXT
         */
        public static final String DATA5 = "data5";
    }

    /**
     * 文本笔记的具体字段与常量定义。
     * 实现了 DataColumns 接口，用于明确各通用列在文本笔记中的含义。
     */
    public static final class TextNote implements DataColumns {
        /**
         * 文本模式：1 为清单模式，0 为普通模式
         * 映射到 DATA1 字段
         */
        public static final String MODE = DATA1;

        // 清单模式标记
        public static final int MODE_CHECK_LIST = 1;

        // 文本笔记的内容类型（多条记录目录）
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/text_note";

        // 文本笔记的内容单项类型
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/text_note";

        // 文本笔记的 Uri
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/text_note");
    }

    /**
     * 通话记录笔记的具体字段与常量定义。
     * 实现了 DataColumns 接口，用于明确各通用列在通话记录中的含义。
     */
    public static final class CallNote implements DataColumns {
        /**
         * 通话日期（映射到 DATA1）
         * 类型: INTEGER (long)
         */
        public static final String CALL_DATE = DATA1;

        /**
         * 电话号码（映射到 DATA3）
         * 类型: TEXT
         */
        public static final String PHONE_NUMBER = DATA3;

        // 通话记录的内容类型（多条记录目录）
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/call_note";

        // 通话记录的内容单项类型
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/call_note";

        // 通话记录的 Uri
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/call_note");
    }
}
