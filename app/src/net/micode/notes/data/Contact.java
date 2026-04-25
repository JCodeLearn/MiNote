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

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.util.HashMap;

/**
 * 联系人查询工具类。
 * 通过系统通讯录根据电话号码查找联系人姓名，并提供内存缓存以加速重复查询。
 */
public class Contact {
    /**
     * 电话号码 -> 联系人姓名 的内存缓存，避免重复查询系统数据库。
     */
    private static HashMap<String, String> sContactCache;
    
    // 日志标签
    private static final String TAG = "Contact";

    /**
     * 联系人查询的 SQLite 选择条件。
     * 使用电话匹配函数 PHONE_NUMBERS_EQUAL 和来电号码最小匹配长度来精确查找。
     * 占位符 '+' 将在运行时替换为 phoneNumber 的最小匹配串。
     */
    private static final String CALLER_ID_SELECTION = "PHONE_NUMBERS_EQUAL(" + Phone.NUMBER
    + ",?) AND " + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'"
    + " AND " + Data.RAW_CONTACT_ID + " IN "
            + "(SELECT raw_contact_id "
            + " FROM phone_lookup"
            + " WHERE min_match = '+')";

    /**
     * 根据电话号码查询联系人姓名。
     * 优先使用缓存，未命中时查询系统通讯录数据库，并将结果存入缓存。
     *
     * @param context     上下文，用于获取 ContentResolver
     * @param phoneNumber 要查询的电话号码（完整号码）
     * @return 对应的联系人姓名，若未找到则返回 null
     */
    public static String getContact(Context context, String phoneNumber) {
        // 首次调用时初始化缓存
        if (sContactCache == null) {
            sContactCache = new HashMap<String, String>();
        }

        // 如果缓存中已有该号码，直接返回
        if (sContactCache.containsKey(phoneNumber)) {
            return sContactCache.get(phoneNumber);
        }

        // 用最小匹配串替换查询条件中的占位符 '+'，提高匹配成功率
        String selection = CALLER_ID_SELECTION.replace("+",
                PhoneNumberUtils.toCallerIDMinMatch(phoneNumber));
        // 查询 ContactsContract.Data 表，获取联系人姓名
        Cursor cursor = context.getContentResolver().query(
                Data.CONTENT_URI,
                new String [] { Phone.DISPLAY_NAME },
                selection,
                new String[] { phoneNumber },
                null);

        if (cursor != null && cursor.moveToFirst()) {
            try {
                // 获取姓名并缓存
                String name = cursor.getString(0);
                sContactCache.put(phoneNumber, name);
                return name;
            } catch (IndexOutOfBoundsException e) {
                // 防止 cursor 意外无数据时 getString 抛异常
                Log.e(TAG, " Cursor get string error " + e.toString());
                return null;
            } finally {
                cursor.close(); // 确保关闭游标
            }
        } else {
            // 没有匹配到任何联系人
            Log.d(TAG, "No contact matched with number:" + phoneNumber);
            return null;
        }
    }
}
