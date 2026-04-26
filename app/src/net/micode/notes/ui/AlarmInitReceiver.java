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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;


/**
 * 开机启动/应用初始化的广播接收器
 * 负责在系统启动或相关初始化事件回调时，重新查询并注册所有未过期的便签提醒到系统的 AlarmManager
 */
public class AlarmInitReceiver extends BroadcastReceiver {

    // 数据库查询所需的列名，包括 ID 和 提醒时间
    private static final String [] PROJECTION = new String [] {
        NoteColumns.ID,
        NoteColumns.ALERTED_DATE
    };

    private static final int COLUMN_ID                = 0;
    private static final int COLUMN_ALERTED_DATE      = 1;

    /**
     * 接收到广播时的回调方法
     * 查找数据库中所有提醒时间大于当前时间且类型为普通便签的记录，并将其设置到 AlarmManager 中
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        long currentDate = System.currentTimeMillis();
        Cursor c = context.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                PROJECTION,
                NoteColumns.ALERTED_DATE + ">? AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE,
                new String[] { String.valueOf(currentDate) },
                null);

        if (c != null) {
            if (c.moveToFirst()) {
                do {
                    long alertDate = c.getLong(COLUMN_ALERTED_DATE);
                    Intent sender = new Intent(context, AlarmReceiver.class);
                    // 将便签 ID 附加在 URI 后面，方便 AlarmReceiver 接收后知道是哪个便签
                    sender.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, c.getLong(COLUMN_ID)));
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, sender, 0);
                    AlarmManager alermManager = (AlarmManager) context
                            .getSystemService(Context.ALARM_SERVICE);
                    // 使用 RTC_WAKEUP，即使设备休眠也会唤醒设备来触发提醒
                    alermManager.set(AlarmManager.RTC_WAKEUP, alertDate, pendingIntent);
                } while (c.moveToNext());
            }
            c.close();
        }
    }
}
