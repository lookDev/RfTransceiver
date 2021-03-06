package com.rftransceiver.db;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import com.rftransceiver.util.DataClearnManager;
import com.rftransceiver.adapter.ListConversationAdapter;
import com.rftransceiver.datasets.ContactsData;
import com.rftransceiver.datasets.ConversationData;
import com.rftransceiver.fragments.HomeFragment;
import com.rftransceiver.group.GroupEntity;
import com.rftransceiver.group.GroupMember;
import com.rftransceiver.util.Constants;
import com.rftransceiver.util.GroupUtil;
import com.rftransceiver.util.ImageUtil;
import com.rftransceiver.util.PoolThreadUtil;

import java.io.File;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Created by rantianhua on 15-6-27.
 */
public class DBManager {

    private DatabaseHelper helper;
    //管理数据库
    private SQLiteDatabase db;
    //缓存图片路径
    private ArrayList<File> fileList = new ArrayList<>();
    //保存图片的目录
    private File picDir;

    private static DBManager dbManager;

    private SharedPreferences sp;
    //缓存聊天记录
    private List<ContentValues> listChats = new ArrayList<>();

    private DBManager(Context context) {
        helper = new DatabaseHelper(context);
        picDir = context.getExternalCacheDir();
        sp = context.getSharedPreferences(Constants.SP_USER,0);
    }

    public static DBManager getInstance(Context context) {
        if(dbManager == null) {
            dbManager = new DBManager(context);
        }
        return dbManager;
    }

    //
    public ArrayList<File> getFileList(){
        return fileList;
    }

    private void openWriteDB() {
        while (db != null && db.isOpen()) {
            Thread.currentThread();
            try {
                Thread.sleep(50);
            }catch (Exception e) {
                e.printStackTrace();
            }
        }
        db = helper.getWritableDatabase();
    }

    private synchronized void openReadDB() {
        while (db != null && db.isOpen() && !db.isReadOnly()) {
            Thread.currentThread();
            try {
                Thread.sleep(50);
            }catch (Exception e) {
                e.printStackTrace();
            }
        }
        db = helper.getReadableDatabase();
    }

    /**
     * save a group into database
     * @param groupEntity a group data
     */
    public void saveGroup(GroupEntity groupEntity) {
        String name = groupEntity.getName();
        byte[] async = groupEntity.getAsyncWord();
        String asyncWord = null;
        if(async != null) {
            asyncWord = Base64.encodeToString(async,Base64.DEFAULT);
        }
        if(TextUtils.isEmpty(name) || TextUtils.isEmpty(asyncWord)) return;
        try {
            openWriteDB();
            db.beginTransaction();
            //插入组的基本信息
            db.execSQL("INSERT INTO " + DatabaseHelper.TABLE_GROUP + " VALUES(null,?,?,?)",
                    new Object[]{name,asyncWord,groupEntity.getTempId()});
            //得到刚插入的组id（在数据库中的id）
            int gid = -1;
            Cursor cursor = db.rawQuery("select last_insert_rowid() from " + DatabaseHelper.TABLE_GROUP,
                    null);
            if(cursor != null && cursor.moveToFirst()) {
                gid = cursor.getInt(0);

                Constants.GROUPID = gid;//保存该组的id到Constants中，用于通讯录中与选中组的id进行比较判断删除时是否是正在聊天的组

                cursor.close();
            }
            //保存组的成员
            List<GroupMember> members = groupEntity.getMembers();
            if(gid != -1 && members != null && members.size() > 1) {
                //更新当前组的id
                GroupUtil.saveCurrentGid(gid,sp);
                for(GroupMember member : members) {
                    //保存图片到sd卡
                    Bitmap bitmap = member.getBitmap();
                    String bpath = null;
                    if(bitmap != null) {
                        String picName = System.currentTimeMillis() + ".jpg";
                        File file = new File(picDir,picName);
                        ImageUtil.savePicToLocal(file,bitmap);
                        bpath = file.getAbsolutePath();
                    }
                    String sql = "INSERT INTO " + DatabaseHelper.TABLE_MEMBER + " VALUES(?,?,?,?)";
                    db.execSQL(sql,new Object[]{gid,member.getId(),member.getName(),bpath});
                }
            }
            db.setTransactionSuccessful();
            db.endTransaction();
        }catch (Exception e) {
            Log.e("saveGroup","error in save group base info or members info",e);
        }finally {
            closeDB();
        }
    }

    public void deleteGroup(int gid) {//很据组的id删除表的信息
        try{

            openWriteDB();//把原来的openReadD方法改成openWrite方法

            db.beginTransaction();
            db.delete(DatabaseHelper.TABLE_DATA,"_gid = ?",new String[]{String.valueOf(gid)});

            db.delete(DatabaseHelper.TABLE_MEMBER,"_gid = ?",new String[]{String.valueOf(gid)});
            db.delete(DatabaseHelper.TABLE_GROUP,"_gid = ?",new String[]{String.valueOf(gid)});
            db.setTransactionSuccessful();
            db.endTransaction();
        }catch (Exception e) {
            Log.e("saveGroup","error in save group base info or members info",e);
        }finally {
            closeDB();
        }
    }
    public void changeImfor(int gid ,String path,String name){//修改数据库中的自己的头像和名字 然而并没有用上
        try{
            openWriteDB();
            db.beginTransaction();
            ContentValues cv = new ContentValues();
            cv.put("_photopath",path);
            cv.put("_nickname",name);
            db.update(DatabaseHelper.TABLE_MEMBER, cv ,"_gid = ?" ,new String[]{String.valueOf(gid)});
            db.setTransactionSuccessful();
            db.endTransaction();
        }catch (Exception e) {
            Log.e("saveGroup","error in save group base info or members info",e);
        }finally {
            closeDB();
        }
    }
    /**
     * 关闭数据库
     */
    private synchronized void closeDB() {
        if(db != null) {
            try {
                db.close();
            }catch (Exception w) {
                w.printStackTrace();
            }
        }
    }

    public static void close() {
        if(dbManager != null) {
            dbManager.closeDB();
            dbManager.listChats = null;
            dbManager = null;
        }
    }

    /**
     * get a group data by gid
     * @param gid
     * @return
     */
    public GroupEntity getAgroup(int gid) {
        GroupEntity groupEntity = null;
        try {
            openReadDB();
            db.beginTransaction();
            Cursor cursor = db.rawQuery("select * from " + DatabaseHelper.TABLE_GROUP +
                " where _gid=" + gid,null);
            String name = null,async = null;
            int myId = -1;
            if(cursor != null && cursor.moveToFirst()) {
                name = cursor.getString(cursor.getColumnIndex("_gname"));
                async = cursor.getString(cursor.getColumnIndex("_syncword"));
                myId = cursor.getInt(cursor.getColumnIndex("_myId"));
                cursor.close();
            }
            if(!TextUtils.isEmpty(name) && !TextUtils.isEmpty(async)) {
                byte[] asyncword = Base64.decode(async,Base64.DEFAULT);
                groupEntity = new GroupEntity(name,asyncword);
                if(myId != -1) {
                    groupEntity.setTempId(myId);
                }
            }

            if(groupEntity != null) {
                //find all members
                String sql = "select * from " + DatabaseHelper.TABLE_MEMBER
                        + " where _gid=" + gid;
                Cursor cur = db.rawQuery(sql,null);
                if(cur != null) {
                    while (cur.moveToNext()) {
                        GroupMember groupMember = new GroupMember();
                        String data = cur.getString(cur.getColumnIndex("_nickname"));
                        groupMember.setName(data);
                        int mid = cur.getInt(cur.getColumnIndex("_mid"));
                        groupMember.setId(mid);
                        data = cur.getString(cur.getColumnIndex("_photopath"));
                        if(!TextUtils.isEmpty(data)) {
                            Bitmap bitmap = BitmapFactory.decodeFile(data);
                            if(bitmap != null) {
                                groupMember.setBitmap(bitmap);
                                bitmap = null;
                            }
                        }
                        groupEntity.getMembers().add(groupMember);
                    }
                    cur.close();
                }
            }
            db.setTransactionSuccessful();
            db.endTransaction();
        }catch (Exception e) {
            Log.e("getAgroup","error in getAgroup",e);
        }finally {
            closeDB();
        }
        return groupEntity;
    }

    /**
     * save chat data to db
     * @param data
     * @param type 0 is sounds data
     *             1 is text
     *             2 is address
     *             3 is picture
     *             4 is time data
     */
    public void readyMessage(Object data,int type,int memberId,int groupId,long timestamp) {
        ContentValues values = new ContentValues();
        String saveData = null;
        if(type == 3) {
            //save picture to local dir
            String picName = System.currentTimeMillis() + ".jpg";
            try {
                File file = new File(picDir,picName);
                Bitmap bitmap = (Bitmap)data;
                ImageUtil.savePicToLocal(file,bitmap);
                saveData = file.getAbsolutePath();
            }catch (Exception e) {
                ;e.printStackTrace();
            }
        }else {
            saveData = (String) data;
        }
        values.put("_date_time",timestamp);
        values.put("_gid",groupId);
        values.put("_mid",memberId);
        values.put("_type", type);
        values.put("_data", saveData);
        listChats.add(values);
        if(listChats.size() > 9) {
            saveMessage();
        }
    }

    //save data to db
    public void saveMessage() {
        if(listChats.size() == 0) return;
        final List<ContentValues> saveValues = new ArrayList<>();
        saveValues.addAll(listChats);
        listChats.clear();
        PoolThreadUtil.getInstance().addTask(new Runnable() {
            @Override
            public void run() {
                try {
                    openWriteDB();
                    db.beginTransaction();
                    for (ContentValues values : saveValues) {
                        long re = db.insert(DatabaseHelper.TABLE_DATA, "_data", values);
                    }
                    db.setTransactionSuccessful();
                    db.endTransaction();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    closeDB();
                }
            }
        });
    }
    public void deleteMessage(int gid) {//根据组的id来删除其聊天记录
        try{
            openWriteDB();
            db.beginTransaction();
            db.delete(DatabaseHelper.TABLE_DATA, "_gid = ?", new String[]{String.valueOf(gid)});
            db.setTransactionSuccessful();
            db.endTransaction();
        }catch (Exception e) {
            Log.e("saveGroup","error in save group base info or members info",e);
        }finally {
            closeDB();
        }
    }

    public String getCacheInformation(){//得到Message表中的信息大小
        StringBuffer stringBuffer = new StringBuffer();
        long size = 0;
        try{
            openReadDB();
            db.beginTransaction();
            Cursor cursor = db.rawQuery("select * from " + DatabaseHelper.TABLE_DATA,
                    null);
            while(cursor.moveToNext())
            {
                int type = cursor.getInt(cursor.getColumnIndex("_type"));
                String data = cursor.getString(cursor.getColumnIndex("_data"));
                if(type == 3)
                {
                    File f = new File(data);
                    if(f.isFile()) {
                        size = size + DataClearnManager.getFolderSize(f);
                        fileList.add(f);
                    }
                    else
                        stringBuffer.append(data);
                }
                else
                    stringBuffer.append(data);
            }
            db.setTransactionSuccessful();
            db.endTransaction();
            size = size + DataClearnManager.getStringSize(stringBuffer.toString());
        }catch (Exception e) {
            Log.e("saveGroup","error in save group base info or members info",e);
        }finally {
            closeDB();
        }
        return DataClearnManager.getFormatSize((double)size);
    }

    public void deleteCache(){//删除存放data的表
        try{
            openReadDB();
            db.beginTransaction();
            db.execSQL("DELETE FROM " + DatabaseHelper.TABLE_DATA);
            db.setTransactionSuccessful();
            db.endTransaction();
        }catch (Exception e) {
            Log.e("saveGroup","error in save group base info or members info",e);
        }finally {
            closeDB();
        }

    }
    public void   updateMyMessage(String name,String path){//修改我在数据库中的信息
        try{
            openWriteDB();
            db.beginTransaction();
            ContentValues contentValues =new ContentValues();
            contentValues.put("_nickname",name);
            contentValues.put("_photopath",path);
            Cursor cursor = db.rawQuery("select * from " + DatabaseHelper.TABLE_GROUP,null);
            if(cursor != null) {
                while (cursor.moveToNext()) {
                    int gid = cursor.getInt(cursor.getColumnIndex("_gid"));
                    int id = cursor.getInt(cursor.getColumnIndex("_myId"));
                    db.update(DatabaseHelper.TABLE_MEMBER,contentValues," _mid= ? and _gid= ? ",new String[]{String.valueOf(id),String.valueOf(gid)});
                        }
                      cursor.close();
                    }
            db.setTransactionSuccessful();
            db.endTransaction();
        }catch (Exception e) {
            Log.e("saveGroup","error in save group base info or members info",e);
        }finally {
            closeDB();
        }
    }

    /**
     * 获取聊天记录
     * @param gid group id of the data
     * @param limits how many datas get
     */
    public List<ConversationData> getConversationData(int gid,int myId,long timeStamp,int limits) {
        String sql = "select * from " + DatabaseHelper.TABLE_DATA +
                " where _gid=" + gid;
        if(timeStamp > 0) {
            sql += " and _date_time < "+ timeStamp;
        }
        sql += " order by _date_time desc " + "limit " + limits;
        List<ConversationData> conversationDatas = null;
        try {
            openReadDB();
            db.beginTransaction();
            Cursor cursor = db.rawQuery(sql,null);
            conversationDatas = new ArrayList<>();
            int count  = cursor.getCount() -1;
            while (count >= 0) {
                cursor.moveToPosition(count);
                count--;
                int messageId = cursor.getInt(cursor.getColumnIndex("_messageid"));
                int type = cursor.getInt(cursor.getColumnIndex("_type"));
                int mid = cursor.getInt(cursor.getColumnIndex("_mid"));
                String text = cursor.getString(cursor.getColumnIndex("_data"));
                String time = cursor.getString(cursor.getColumnIndex("_date_time"));

                ConversationData data = null;
                ListConversationAdapter.ConversationType conType = null;
                Bitmap bitmapData = null;
                String address = null;
                switch (type) {
                    case 0: //sounds data
                        conType = mid == myId ? ListConversationAdapter.ConversationType.RIGHT_SOUNDS
                                : ListConversationAdapter.ConversationType.LEFT_SOUNDS;
                        break;
                    case 1: //text data
                        conType = mid == myId ? ListConversationAdapter.ConversationType.RIGHT_TEXT
                                : ListConversationAdapter.ConversationType.LEFT_TEXT;
                        break;
                    case 2: //address data
                        conType = mid == myId ? ListConversationAdapter.ConversationType.RIGHT_ADDRESS
                                : ListConversationAdapter.ConversationType.LEFT_ADDRESS;
                        address = text;
                        text = null;
                        break;
                    case 3: //picture data
                        conType = mid == myId ? ListConversationAdapter.ConversationType.RIGHT_PIC
                                : ListConversationAdapter.ConversationType.LEFT_PIC;
                        try {
                            bitmapData = BitmapFactory.decodeFile(text);
                        }catch(Exception e) {

                        }
                        break;
                    case 4:
                        conType = ListConversationAdapter.ConversationType.TIME;
                        break;
                    default:
                        break;
                }

                if(conType == null) break;
                data = new ConversationData(conType);
                data.setDateTime(Long.valueOf(time));
                data.setMid(mid);
                if(bitmapData != null) {
                    data.setBitmap(bitmapData);
                }else if (address != null){
                    data.setAddress(address);
                    address = null;
                }else {
                    data.setContent(text);
                    text = null;
                }
                conversationDatas.add(data);
            }
            cursor.close();
            db.setTransactionSuccessful();
            db.endTransaction();
        }catch (Exception e) {
            e.printStackTrace();
            Log.e("getData", "error ", e);
        }finally {
            closeDB();
        }

        return conversationDatas;
    }

    public List<ContactsData> getContacts() {
        String sql = "select _gname,_gid from " + DatabaseHelper.TABLE_GROUP;
        List<ContactsData> contactsDatas = null;
        try {
            Thread.sleep(300);
        }catch (Exception e2) {

        }
        try {
            openReadDB();
            db.beginTransaction();
            Cursor cursor = db.rawQuery(sql,null);
            if(cursor == null) return null;
            if(cursor.getCount() > 0) {
                contactsDatas = new ArrayList<>();
            }
            if(contactsDatas != null) {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(0);
                    int gid = cursor.getInt(1);
                    ContactsData data = new ContactsData(name,gid);
                    contactsDatas.add(data);
                }
            }
            cursor.close();
            db.setTransactionSuccessful();
            db.endTransaction();
        }catch (Exception e) {
            e.printStackTrace();
        }finally {
            closeDB();
        }
        return contactsDatas;
    }
}
