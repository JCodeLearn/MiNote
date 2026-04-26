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

// ResourceParser：资源解析工具类，负责将颜色/字体大小常量映射到具体的 Android 资源 ID
package net.micode.notes.tool;

import android.content.Context;
import android.preference.PreferenceManager;

import net.micode.notes.R;
import net.micode.notes.ui.NotesPreferenceActivity;

/**
 * ResourceParser：便签界面资源配置的集中管理类。
 * <p>
 * 小米便签支持多种背景颜色（黄、蓝、白、绿、红）和字体大小（小、中、大、超大）。
 * 该类通过内部类将不同 UI 场景（编辑页、列表项、小部件、文字外观）所需的资源数组
 * 和获取方法组织在一起，实现了界面配色与代码逻辑的分离。
 * </p>
 * <p>
 * 同时提供从 SharedPreference 读取用户设置、返回随机背景色等辅助方法。
 * </p>
 */
public class ResourceParser {

    // ==================== 背景颜色常量 ====================
    // 五种内置背景颜色，用整型 ID 表示
    public static final int YELLOW           = 0;   // 黄色
    public static final int BLUE             = 1;   // 蓝色
    public static final int WHITE            = 2;   // 白色
    public static final int GREEN            = 3;   // 绿色
    public static final int RED              = 4;   // 红色

    public static final int BG_DEFAULT_COLOR = YELLOW;  // 默认背景色 = 黄色

    // ==================== 字体大小常量 ====================
    // 四种内置字体大小，用整型 ID 表示
    public static final int TEXT_SMALL       = 0;   // 小号字体
    public static final int TEXT_MEDIUM      = 1;   // 中号字体
    public static final int TEXT_LARGE       = 2;   // 大号字体
    public static final int TEXT_SUPER       = 3;   // 超大号字体

    public static final int BG_DEFAULT_FONT_SIZE = TEXT_MEDIUM;  // 默认字体大小 = 中号

    // ==================== 编辑页面背景资源 ====================
    /**
     * NoteBgResources：编辑页面的背景资源容器。
     * <p>
     * 编辑页分为两部分：标题栏背景和正文区域背景。
     * 每种颜色分别对应两套 drawable 资源。
     * </p>
     */
    public static class NoteBgResources {
        // 正文编辑区域背景资源数组，索引与颜色常量一一对应
        private final static int [] BG_EDIT_RESOURCES = new int [] {
                R.drawable.edit_yellow,   // 0: 黄色正文背景
                R.drawable.edit_blue,     // 1: 蓝色正文背景
                R.drawable.edit_white,    // 2: 白色正文背景
                R.drawable.edit_green,    // 3: 绿色正文背景
                R.drawable.edit_red       // 4: 红色正文背景
        };

        // 标题栏背景资源数组，索引与颜色常量一一对应
        private final static int [] BG_EDIT_TITLE_RESOURCES = new int [] {
                R.drawable.edit_title_yellow,   // 0: 黄色标题栏背景
                R.drawable.edit_title_blue,     // 1: 蓝色标题栏背景
                R.drawable.edit_title_white,    // 2: 白色标题栏背景
                R.drawable.edit_title_green,    // 3: 绿色标题栏背景
                R.drawable.edit_title_red       // 4: 红色标题栏背景
        };

        /**
         * 根据颜色 ID 获取正文编辑区域的背景资源
         * @param id 颜色 ID（YELLOW/BLUE/WHITE/GREEN/RED）
         * @return 对应的 drawable 资源 ID
         */
        public static int getNoteBgResource(int id) {
            return BG_EDIT_RESOURCES[id];
        }

        /**
         * 根据颜色 ID 获取标题栏的背景资源
         * @param id 颜色 ID（YELLOW/BLUE/WHITE/GREEN/RED）
         * @return 对应的 drawable 资源 ID
         */
        public static int getNoteTitleBgResource(int id) {
            return BG_EDIT_TITLE_RESOURCES[id];
        }
    }

    // ==================== 默认背景 ID 获取 ====================
    /**
     * 获取新建便签的默认背景颜色 ID。
     * <p>
     * 如果用户在设置中开启了"随机背景色"，则从五种颜色中随机选择一种；
     * 否则返回默认颜色（黄色）。
     * </p>
     *
     * @param context 上下文
     * @return 背景颜色 ID
     */
    public static int getDefaultBgId(Context context) {
        // 读取 SharedPreference 中是否开启了随机背景色设置
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                NotesPreferenceActivity.PREFERENCE_SET_BG_COLOR_KEY, false)) {
            // 随机选择一种背景色
            return (int) (Math.random() * NoteBgResources.BG_EDIT_RESOURCES.length);
        } else {
            // 返回默认背景色（黄色）
            return BG_DEFAULT_COLOR;
        }
    }

    // ==================== 列表项背景资源 ====================
    /**
     * NoteItemBgResources：便签列表项的背景资源容器。
     * <p>
     * 为了使列表项之间有圆角分隔效果，列表项被分为四种状态：
     * 首项（上圆角）、中间项（无圆角）、末项（下圆角）、单项（四角圆角）。
     * 每种状态 × 五种颜色 = 20 个 drawable 资源。
     * </p>
     */
    public static class NoteItemBgResources {
        // 列表第一项的背景资源（上圆角）
        private final static int [] BG_FIRST_RESOURCES = new int [] {
                R.drawable.list_yellow_up,
                R.drawable.list_blue_up,
                R.drawable.list_white_up,
                R.drawable.list_green_up,
                R.drawable.list_red_up
        };

        // 列表中间项的背景资源（无圆角）
        private final static int [] BG_NORMAL_RESOURCES = new int [] {
                R.drawable.list_yellow_middle,
                R.drawable.list_blue_middle,
                R.drawable.list_white_middle,
                R.drawable.list_green_middle,
                R.drawable.list_red_middle
        };

        // 列表最后一项的背景资源（下圆角）
        private final static int [] BG_LAST_RESOURCES = new int [] {
                R.drawable.list_yellow_down,
                R.drawable.list_blue_down,
                R.drawable.list_white_down,
                R.drawable.list_green_down,
                R.drawable.list_red_down,
        };

        // 列表中只有一项时的背景资源（四角圆角）
        private final static int [] BG_SINGLE_RESOURCES = new int [] {
                R.drawable.list_yellow_single,
                R.drawable.list_blue_single,
                R.drawable.list_white_single,
                R.drawable.list_green_single,
                R.drawable.list_red_single
        };

        /** 获取列表第一项的背景资源 */
        public static int getNoteBgFirstRes(int id) {
            return BG_FIRST_RESOURCES[id];
        }

        /** 获取列表最后一项的背景资源 */
        public static int getNoteBgLastRes(int id) {
            return BG_LAST_RESOURCES[id];
        }

        /** 获取单项列表的背景资源 */
        public static int getNoteBgSingleRes(int id) {
            return BG_SINGLE_RESOURCES[id];
        }

        /** 获取列表中间项的背景资源 */
        public static int getNoteBgNormalRes(int id) {
            return BG_NORMAL_RESOURCES[id];
        }

        /** 获取文件夹列表项的背景资源（文件夹只有一种白色样式） */
        public static int getFolderBgRes() {
            return R.drawable.list_folder;
        }
    }

    // ==================== 桌面小部件背景资源 ====================
    /**
     * WidgetBgResources：桌面小部件的背景资源容器。
     * <p>
     * 支持 2x 和 4x 两种尺寸的小部件，每种尺寸 × 五种颜色。
     * </p>
     */
    public static class WidgetBgResources {
        // 2x 尺寸小部件背景
        private final static int [] BG_2X_RESOURCES = new int [] {
                R.drawable.widget_2x_yellow,
                R.drawable.widget_2x_blue,
                R.drawable.widget_2x_white,
                R.drawable.widget_2x_green,
                R.drawable.widget_2x_red,
        };

        public static int getWidget2xBgResource(int id) {
            return BG_2X_RESOURCES[id];
        }

        // 4x 尺寸小部件背景
        private final static int [] BG_4X_RESOURCES = new int [] {
                R.drawable.widget_4x_yellow,
                R.drawable.widget_4x_blue,
                R.drawable.widget_4x_white,
                R.drawable.widget_4x_green,
                R.drawable.widget_4x_red
        };

        public static int getWidget4xBgResource(int id) {
            return BG_4X_RESOURCES[id];
        }
    }

    // ==================== 文字外观资源（字体大小） ====================
    /**
     * TextAppearanceResources：文字外观样式资源容器。
     * <p>
     * 支持四种字体大小：小号、中号、大号、超大号。
     * </p>
     */
    public static class TextAppearanceResources {
        // 字体样式资源数组，索引对应 TEXT_SMALL(0) ~ TEXT_SUPER(3)
        private final static int [] TEXTAPPEARANCE_RESOURCES = new int [] {
                R.style.TextAppearanceNormal,   // 小号字体样式
                R.style.TextAppearanceMedium,   // 中号字体样式
                R.style.TextAppearanceLarge,    // 大号字体样式
                R.style.TextAppearanceSuper     // 超大号字体样式
        };

        /**
         * 根据字体大小 ID 获取对应的文字样式资源。
         * <p>
         * 包含一个防御性检查：如果传入的 ID 超出数组长度（例如从旧版本升级导致
         * SharedPreference 中存储了无效值），则安全回退到默认字体大小。
         * </p>
         *
         * @param id 字体大小 ID（TEXT_SMALL / TEXT_MEDIUM / TEXT_LARGE / TEXT_SUPER）
         * @return 对应的 style 资源 ID
         */
        public static int getTexAppearanceResource(int id) {
            /**
             * HACKME: 修复 SharedPreference 中可能存储了无效资源 ID 的 Bug。
             * 当 ID 超出数组长度时，安全回退到默认字体大小（TEXT_MEDIUM），
             * 避免数组越界崩溃。
             */
            if (id >= TEXTAPPEARANCE_RESOURCES.length) {
                return BG_DEFAULT_FONT_SIZE;
            }
            return TEXTAPPEARANCE_RESOURCES[id];
        }

        /**
         * 获取字体样式的种类数量
         * @return 资源数组的长度（当前为 4）
         */
        public static int getResourcesSize() {
            return TEXTAPPEARANCE_RESOURCES.length;
        }
    }
}