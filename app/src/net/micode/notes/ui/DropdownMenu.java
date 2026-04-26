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

package net.micode.notes.ui;

import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;

import net.micode.notes.R;

/**
 * 封装下拉菜单功能的辅助类。
 * 将指定的 Button 绑定一个 PopupMenu，当点击该 Button 时弹出菜单。
 */
public class DropdownMenu {
    private Button mButton;
    private PopupMenu mPopupMenu;
    private Menu mMenu;

    /**
     * 构造函数
     * @param context 上下文
     * @param button 作为锚点并触发菜单的按钮控件
     * @param menuId 菜单的布局资源 ID
     */
    public DropdownMenu(Context context, Button button, int menuId) {
        mButton = button;
        mButton.setBackgroundResource(R.drawable.dropdown_icon);
        mPopupMenu = new PopupMenu(context, mButton);
        mMenu = mPopupMenu.getMenu();
        mPopupMenu.getMenuInflater().inflate(menuId, mMenu);
        mButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mPopupMenu.show();
            }
        });
    }

    /**
     * 设置菜单项的点击监听器
     */
    public void setOnDropdownMenuItemClickListener(OnMenuItemClickListener listener) {
        if (mPopupMenu != null) {
            mPopupMenu.setOnMenuItemClickListener(listener);
        }
    }

    /**
     * 根据 id 查找并返回菜单项
     */
    public MenuItem findItem(int id) {
        return mMenu.findItem(id);
    }

    /**
     * 设置绑定按钮上显示的文字
     */
    public void setTitle(CharSequence title) {
        mButton.setText(title);
    }
}
