package com.example.a61915.server;


import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.example.a61915.utils.SharedPerferenceUtil;

import java.util.ArrayList;
import java.util.List;

public class GetMemoryServer extends AccessibilityService {
    //微信聊天界面中，item的总个数
    private static final String WECHAT_UI_LIST_COUNT = "WECHAT_UI_LIST_COUNT";
    //窗口节点信息，放置在AccessibilityNodeInfo中，本质是个树型结构
    private List<AccessibilityNodeInfo> parents;
    /**
     * 键盘锁的对象,用于红包软件在手机锁屏（没有密码的情况下）时，点亮屏幕进行解锁
     */
    private KeyguardManager.KeyguardLock kl;

    private boolean getMoney = false;//是否模拟点击进行抢红包
    private boolean isFromNotification  = false;//是否从Notification进入微信聊天界面
    private PowerManager.WakeLock wl;
    private Context context;

    private SharedPerferenceUtil sharedPerferenceUtil;
    private String IS_RUNNING_SERVICE = "is_running_service";
    //是不是第一次进入
    private boolean firstIn = false;
    private boolean isFromChatList  = false;
    private int hongbaoNum = 0;//红包总数
    private String HONBBO_SHU = "hongbao_shu";
    private boolean isnomenory = false;//默认是有钱的

    public GetMemoryServer() {
    }

    /**
     *  当系统连接上你的服务时被调用
     */
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        parents = new ArrayList<>();
        if (context == null) {
            context = getApplicationContext();
        }
        if (sharedPerferenceUtil == null) {
            sharedPerferenceUtil = SharedPerferenceUtil.getInstance(context);
        }
    }

    /**
     *  必须重写的方法：此方法用了接受系统发来的event。
     *  在你注册的event发生是被调用。在整个生命周期会被调用多次。
     */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

        if (context == null) {
            context = getApplicationContext();
        }
        if (sharedPerferenceUtil == null) {
            sharedPerferenceUtil = SharedPerferenceUtil.getInstance(context);
        }
        //服务是否在运行
        boolean isOpen = sharedPerferenceUtil.getBoolean(IS_RUNNING_SERVICE, true);
        if (!isOpen) {
            return;
        }

        //getEventType()：事件类型
        int eventType = event.getEventType();

        String className;
        //根据事件回调类型进行处理
        switch (eventType) {
            /**
             * 当通知栏发生改变时，查看通知中是否有微信红包的字样，有就通过通知栏
             * 跳转到相应的界面
             */
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
                Log.d("AssEvent", "TYPE_NOTIFICATION_STATE_CHANGED");
                /**
                 * CharSequence就是字符序列,String 继承于CharSequence
                 */
                List<CharSequence> texts = event.getText();
                if (!texts.isEmpty()) {
                    // 遍历所有通知，找出红包的通知
                    for (CharSequence text : texts) {
                        String content = text.toString();
                        if (content.contains("[微信红包]")) {
                            //如果此时屏幕是锁屏时，没有密码的锁屏
                            boolean isLock = isScreenLocked2();
                            if (isLock) {
                                //解锁
                                Log.d("demo", "解锁屏幕");
                                AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
                                List<AccessibilityNodeInfo> infos = nodeInfo.findAccessibilityNodeInfosByViewId("com.android.systemui:id/expanded");
                                // 判断锁屏界面的通知是否存在
                                if(infos.size()==0||infos==null){
                                    return;
                                }
                                // 取出第一个
                                AccessibilityNodeInfo info = infos.get(0);
                                AccessibilityNodeInfo parent = info.getParent();
                                Log.d("demo",parent+"屏幕上有com.android.systemui:id/expanded");
                                while (parent != null) {
                                    //如父元素也可点击
                                    if (parent.isClickable()) {
                                        Log.d("demo", "双击");
                                        //就添加到parents中
                                        firstIn = true;
                                        // 双击通知，之后进入聊天列表，TYPE_WINDOW_CONTENT_CHANGED
                                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        break;
                                    }
                                    //再上一parent
                                    parent = parent.getParent();
                                }
                                return;
                            }else // 没有锁屏
                            {
                                if (isFromChatList) {
                                    isFromChatList = false;
                                    return;
                                }
                                //模拟打开通知栏消息，即打开微信
                                /**
                                 * event.getParcelableData()会得到Notification，通知栏
                                 * instanceof用于判断就是Notification
                                 */
                                if (event.getParcelableData() != null && event.getParcelableData() instanceof Notification) {
                                    Notification notification = (Notification) event.getParcelableData();
                                    /**
                                     * PendingIntent 是一种特殊的 Intent ，字面意思可以解释为延迟的 Intent ，
                                     * 用于在某个事件结束后执行特定的 Action 。
                                     * 从上面带 Action 的通知也能验证这一点，当用户点击通知时，才会执行。
                                     *
                                     * 这里就是获得Notification中的PendingIntent，用于跳转到相应的界面
                                     */
                                    PendingIntent pendingIntent = notification.contentIntent;
                                    try {
                                        getMoney = true;
                                        isFromNotification = true;
                                        Log.d("demo", "进入微信");
                                        // 进入微信聊天界面，TYPE_WINDOW_STATE_CHANGED
                                        pendingIntent.send();
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        Log.d("demo", "从Notification跳转失败");
                                    }
                                }
                            }// end else 没有锁屏
                        }// end if (content.contains("[微信红包]"))
                    }// end for (CharSequence text : texts) 结束遍历event的文本信息
                }// end if (!texts.isEmpty())
                break; // case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
            //当窗口的状态发生改变时，如出现了微信红包，就可以进行抢红包了
            /**
             * 从通知栏进入聊天界面，触发TYPE_WINDOW_STATE_CHANGED，
             * 直接从聊天列表界面进入聊天详情界面，触发TYPE_WINDOW_CONTENT_CHANGE
             */
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                Log.d("AssEvent:","TYPE_WINDOW_STATE_CHANGED");
                className = event.getClassName().toString();
                Log.d("EventClassName", className);
                //进入聊天界面
                if (className.equals("com.tencent.mm.ui.LauncherUI")) {
                    //点击最后一个红包
                    Log.d("demo","点击红包");
                    //从Notification进入该界面
                    if(isFromNotification){
                        isFromNotification = false;
                        /**
                         * 如果从Notification进入，就获得界面的中红包的节点，打开最后一个红包则可，
                         * 因为Notification提醒了，就一定有红包
                         */
                        Log.d("demo","from notification");
                        getLastPacket();
                    }
                    if(firstIn){
                        firstIn = false;
                        Log.d("demo","from firstIn");
                        AccessibilityNodeInfo root = getRootInActiveWindow();
                        getListViewLastMoney2(event, root);
                    }
                } else if (className.equals("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyReceiveUI")) {
                    //开红包
                    Log.d("demo","开红包");
                    //com.tencent.mm:id/c31点击红包对应的id，不同版本的微信有所不同
                    AccessibilityNodeInfo Receiveroot = getRootInActiveWindow();

                    List<AccessibilityNodeInfo> infos = Receiveroot.findAccessibilityNodeInfosByText("手慢了，红包派完了");
                    if(infos.size()==0||infos==null){//红包中还有钱，有开字
                        if(getMoney) {
                            Log.d("demo", "红包可以领");
                            inputClick("com.tencent.mm:id/c31");
                            isnomenory = false;
                            hongbaoNum = sharedPerferenceUtil.getInteger(HONBBO_SHU, hongbaoNum);
                            hongbaoNum++;
                            sharedPerferenceUtil.putInteger(HONBBO_SHU,hongbaoNum);
                            getMoney = false;
                        }
                    }else{
                        Log.d("demo", "红包被领完");
                        inputClick("com.tencent.mm:id/c33");
                        isnomenory = true;
                    }


                } else if (className.equals("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI")) {
                    //退出红包
                    Log.d("demo","退出红包");
                    inputClick("com.tencent.mm:id/i2");
                }
                break;
            /**
             * 窗口滚动时*/
            case AccessibilityEvent.TYPE_VIEW_SCROLLED:
                Log.d("AssEvent","TYPE_VIEW_SCROLLED");
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if(root==null){
                    return;
                }
                //获得聊天界面的返回按钮
                List<AccessibilityNodeInfo> exitNodeInfos = root.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/gf");
                //确认该界面是聊天界面
                if (exitNodeInfos != null && exitNodeInfos.size() > 0) {
                    getListViewLastMomey(event, root);
                    if(firstIn){
                        firstIn = false;
                        getListViewLastMoney2(event, root);
                    }
                }
                break;
            /**
             * 从聊天列表进入聊天界面*/
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                Log.d("AssEvent","TYPE_WINDOW_CONTENT_CHANGED");
                className = event.getClassName().toString();
                Log.d("EventClassName", className);
                AccessibilityNodeInfo Listroot = getRootInActiveWindow();
                if(Listroot==null){
                    return;
                }
                if(isnomenory){
                    return;
                }
                //获得聊天界面的返回按钮
                List<AccessibilityNodeInfo> back = Listroot.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/hm");
                Log.d("返回键", back.toString());
                //确认该界面是聊天界面
                if (back.size() <= 0) {
                    return;
                }
                Log.d("demo", "聊天界面");

                //获得聊天列表中的ListView
                List<AccessibilityNodeInfo> chatListInfos = Listroot.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/a_c");
                //判断是否在聊天列表
                if (chatListInfos != null && chatListInfos.size() > 0) {
                    //获得第一个, ListView节点
                    getMoney = true;
                    getLastPacket();
                }
                break;
        }
    }

    /**
     * 得到聊天界面中ListView中的最后一项
     * */
    private void getListViewLastMomey(AccessibilityEvent event, AccessibilityNodeInfo root){
        List<AccessibilityNodeInfo> wechatList = root.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/c48");
        if (wechatList != null && wechatList.size() > 0) {
            //获得第一个
            AccessibilityNodeInfo accessibilityNodeInfo = wechatList.get(0);
            //获取AccessibilityEvent中Item的数目
            int itemCount = event.getItemCount();

            //获得sharedPerference中旧的数目
            int oldCount = sharedPerferenceUtil.getInteger(WECHAT_UI_LIST_COUNT, itemCount);
            //存入新的数目
            sharedPerferenceUtil.putInteger(WECHAT_UI_LIST_COUNT, itemCount);

            Log.d("demo","之前的数目"+Integer.toString(oldCount));
            Log.d("demo","现在的数目"+Integer.toString(itemCount));
            int i = itemCount - oldCount;
            //大于0表示有新信息来
            if (i <= 0) {
                isnomenory = false;
                return;
            }
            //判断最新的数据是否有子元素，这样可以判断是否是红包，因为文字、语言、图片都没有子元素
            AccessibilityNodeInfo nodeInfo = accessibilityNodeInfo.getChild(accessibilityNodeInfo.getChildCount()-1);
            if (nodeInfo == null) {
                return;
            }
            //获取聊天界面的红包id，如果是红包的id，获得领取红包或者查看红包的文字
            List<AccessibilityNodeInfo> getRedMoneyList = nodeInfo.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/a5u");
            if (getRedMoneyList != null && getRedMoneyList.size() > 0) {
                try {
                    getMoney = true;
                    getLastPacket();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private void getListViewLastMoney2(AccessibilityEvent event, AccessibilityNodeInfo root){
        List<AccessibilityNodeInfo> wechatList = root.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/c48");
        if (wechatList != null && wechatList.size() > 0) {
            //获得第一个
            AccessibilityNodeInfo firstMsg = wechatList.get(0);
            //判断最新的数据是否有子元素，这样可以判断是否是红包，因为文字、语言、图片都没有子元素
            int childNum = firstMsg.getChildCount();
            Log.d("demo", "列表聊天数：" + Integer.toString(childNum));
            firstMsg = firstMsg.getChild(0);
            if (firstMsg == null) {
                return;
            }

            Log.d("demo","进入聊天界面");
            firstMsg.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            SystemClock.sleep(1000);
            getLastPacket();
        }
    }

    /**
     * 判断手机屏幕是否锁屏
     * @return
     */
    private boolean isScreenLocked() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean isLocked = pm.isScreenOn();
        return isLocked;
    }

    private boolean isScreenLocked2() {
        KeyguardManager mKeyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        boolean flag = mKeyguardManager.inKeyguardRestrictedInputMode();
        return flag;
    }
    /**
     * 通过ID获取控件，并进行模拟点击
     * @param clickId
     */
    private void inputClick(String clickId) {
        /**
         * 如果配置能够获取窗口内容,则会返回当前活动窗口的根结点
         */
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo != null) {
            //找到id对应的控件
            List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByViewId(clickId);
            for (AccessibilityNodeInfo item : list) {
                //模拟点击
                item.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        }
    }

    /**
     * 获取List中最后一个红包，并进行模拟点击
     */
    private void getLastPacket() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        //传入根节点
        recycle(rootNode);
        //点击最后一个红包，进入红包打开界面
        Log.d("demo", "get last Packet");
        if(parents.size()>0){
            parents.get(parents.size() - 1).performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
    }

    /**
     * 回归函数遍历每一个节点，并将含有"领取红包"存进List中
     *
     * @param info
     */
    public void recycle(AccessibilityNodeInfo info) {
        //只有一个子元素
        if (info.getChildCount() == 0) {
            if (info.getText() != null) {
                /**
                 * 查看红包：领取自己发的，领取红包：领取他人发的
                 */
                if ("查看红包".equals(info.getText().toString())||"领取红包".equals(info.getText().toString())) {
                    if (info.isClickable()) {
                        //模拟点击
                        info.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    }
                    //得到父元素
                    AccessibilityNodeInfo parent = info.getParent();
                    while (parent != null) {
                        //如父元素也可点击
                        if (parent.isClickable()) {
                            //就添加到parents中
                            parents.add(parent);
                            break;
                        }
                        //再上一parent
                        parent = parent.getParent();
                    }

                }
            }
        } else {
            // 遍历所有子元素
            for (int i = 0; i < info.getChildCount(); i++) {
                if (info.getChild(i) != null) {
                    recycle(info.getChild(i));
                }
            }
        }
    }

    /**
     * 在节点的子节点中寻找第一个出现的类名为ClassName的节点
     * */
    public AccessibilityNodeInfo findAccessibilityNodeInfosByViewClass(AccessibilityNodeInfo info, String ClassName){
        if(info.getClassName().equals(ClassName)){
            return info;
        }
        int ChildNum = info.getChildCount();
//        Log.d("demo", Integer.toString(ChildNum));
        if(ChildNum == 0){
            return null;
        }
        for(int i = 0; i < ChildNum; i++){
            if(findAccessibilityNodeInfosByViewClass(info.getChild(i), ClassName) != null){
                return info.getChild(i);
            }
        }
        return null;
    }

    /**
     *  必须重写的方法：系统要中断此service返回的响应时会调用。
     *  在整个生命周期会被调用多次。
     */
    @Override
    public void onInterrupt() {
        if (context == null) {
            context = getApplicationContext();
        }
        if (sharedPerferenceUtil == null) {
            sharedPerferenceUtil = SharedPerferenceUtil.getInstance(context);
        }
    }


    /**
     *  在系统要关闭此service时调用。
     */
    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        startService(new Intent(this, GetMemoryServer.class));
    }

}
