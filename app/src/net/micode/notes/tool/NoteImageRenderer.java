// 新增：用于将笔记内容渲染为图片，支持背景色、字体大小和清单模式
package net.micode.notes.tool;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import net.micode.notes.R;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.tool.ResourceParser.NoteBgResources;
// 导入 NoteEditActivity 以使用清单标记常量
import net.micode.notes.ui.NoteEditActivity;

/**
 * 笔记图片渲染工具类。
 * 将笔记内容和样式渲染到 Bitmap 上，用于分享为图片功能。
 */
public class NoteImageRenderer {

    // 图片宽度（像素），保证清晰度
    private static final int IMAGE_WIDTH = 1080;
    // 左右内边距
    private static final int PADDING_HORIZONTAL = 60;
    // 上下内边距
    private static final int PADDING_TOP = 80;
    private static final int PADDING_BOTTOM = 80;
    // 行间距倍数
    private static final float LINE_SPACING_MULTIPLIER = 1.2f;
    // 清单模式勾选框大小
    private static final int CHECKBOX_SIZE = 40;
    // 勾选框与文字间距
    private static final int CHECKBOX_MARGIN = 20;

    /**
     * 将笔记内容渲染为图片。
     *
     * @param context         上下文，用于获取资源
     * @param content         笔记文本内容
     * @param bgColorId       背景颜色 ID（与 ResourceParser 颜色常量一致）
     * @param fontSizeId      字体大小 ID（与 ResourceParser 字体常量一致）
     * @param isCheckListMode 是否为清单模式
     * @return 生成的 Bitmap，失败返回 null
     */
    public static Bitmap renderNoteToImage(Context context, String content,
                                           int bgColorId, int fontSizeId,
                                           boolean isCheckListMode) {
        if (TextUtils.isEmpty(content)) {
            return null;
        }

        // 1. 准备画笔
        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(context.getResources().getColor(R.color.text_color_primary, null));
        textPaint.setTextSize(getFontSize(context, fontSizeId));
        textPaint.setTypeface(Typeface.DEFAULT);

        // 2. 计算文本布局高度
        int availableWidth = IMAGE_WIDTH - 2 * PADDING_HORIZONTAL;
        StaticLayout layout;
        if (isCheckListMode) {
            // 清单模式：每行前面有勾选框 + 空格，可用宽度需减去勾选框和间距
            int checkOffset = CHECKBOX_SIZE + CHECKBOX_MARGIN;
            layout = buildStaticLayout(content, textPaint, availableWidth - checkOffset);
        } else {
            layout = buildStaticLayout(content, textPaint, availableWidth);
        }

        // 3. 计算图片总高度
        int contentHeight = layout.getHeight();
        int totalHeight = PADDING_TOP + contentHeight + PADDING_BOTTOM;
        // 最小高度保证
        totalHeight = Math.max(totalHeight, 400);

        // 4. 创建 Bitmap 和 Canvas
        Bitmap bitmap = Bitmap.createBitmap(IMAGE_WIDTH, totalHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // 5. 绘制背景
        int bgColor = getBackgroundColor(context, bgColorId);
        canvas.drawColor(bgColor);

        // 6. 绘制文本
        canvas.save();
        canvas.translate(PADDING_HORIZONTAL, PADDING_TOP);

        if (isCheckListMode) {
            // 逐行绘制，带勾选框
            String[] lines = content.split("\n");
            float y = 0;
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float lineHeight = (fm.descent - fm.ascent) * LINE_SPACING_MULTIPLIER;

            for (String line : lines) {
                if (TextUtils.isEmpty(line)) {
                    y += lineHeight;
                    continue;
                }
                boolean isChecked = line.startsWith(NoteEditActivity.TAG_CHECKED);
                // 去掉勾选标记，恢复纯文本
                String pureText = line.replace(NoteEditActivity.TAG_CHECKED, "")
                        .replace(NoteEditActivity.TAG_UNCHECKED, "")
                        .trim();

                // 绘制勾选框
                Paint checkboxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                checkboxPaint.setStyle(Paint.Style.STROKE);
                checkboxPaint.setStrokeWidth(4);
                checkboxPaint.setColor(context.getResources().getColor(R.color.checkbox_border, null));
                int cx = CHECKBOX_SIZE / 2;
                int cy = (int) (y + lineHeight / 2);
                canvas.drawCircle(cx, cy, CHECKBOX_SIZE / 2, checkboxPaint);

                if (isChecked) {
                    // 绘制勾选标志（简单对勾）
                    checkboxPaint.setStyle(Paint.Style.FILL);
                    canvas.drawText("✓", cx - CHECKBOX_SIZE / 4, cy + CHECKBOX_SIZE / 4, checkboxPaint);
                }

                // 绘制文本
                float textX = CHECKBOX_SIZE + CHECKBOX_MARGIN;
                if (isChecked) {
                    // 已勾选：添加删除线效果
                    textPaint.setStrikeThruText(true);
                } else {
                    textPaint.setStrikeThruText(false);
                }
                canvas.drawText(pureText, textX, y - fm.ascent, textPaint);
                y += lineHeight;
            }
        } else {
            // 普通模式：直接绘制整个 StaticLayout
            layout.draw(canvas);
        }
        canvas.restore();

        return bitmap;
    }

    /**
     * 构建 StaticLayout 用于多行文本排版。
     */
    private static StaticLayout buildStaticLayout(String text, TextPaint paint, int width) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0, LINE_SPACING_MULTIPLIER)
                    .setIncludePad(true)
                    .build();
        } else {
            // 兼容旧版本
            return new StaticLayout(text, paint, width,
                    Layout.Alignment.ALIGN_NORMAL, LINE_SPACING_MULTIPLIER, 0.0f, true);
        }
    }

    /**
     * 根据背景颜色 ID 获取实际 ARGB 颜色值。
     */
    private static int getBackgroundColor(Context context, int bgColorId) {
        int[] colorRes = {
                R.color.note_bg_yellow,
                R.color.note_bg_blue,
                R.color.note_bg_white,
                R.color.note_bg_green,
                R.color.note_bg_red
        };
        if (bgColorId >= 0 && bgColorId < colorRes.length) {
            return context.getResources().getColor(colorRes[bgColorId], null);
        }
        return context.getResources().getColor(R.color.note_bg_yellow, null);
    }

    /**
     * 根据字体大小 ID 获取实际像素大小。
     */
    private static float getFontSize(Context context, int fontSizeId) {
        switch (fontSizeId) {
            case ResourceParser.TEXT_SMALL:
                return 40;
            case ResourceParser.TEXT_MEDIUM:
                return 52;
            case ResourceParser.TEXT_LARGE:
                return 64;
            case ResourceParser.TEXT_SUPER:
                return 76;
            default:
                return 52;
        }
    }
}