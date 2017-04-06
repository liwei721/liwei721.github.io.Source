---
title: Android的NFC技术介绍
date: 2017-03-21 16:35:10
tags: android, nfc
categories: Android技术
---
## 背景
- 近距离无线通信技术（Near Field Communication ，NFC），是由飞利浦公司和索尼公司共同开发的一中非接触式识别和互联技术，可以在移动设备、消费类电子产品、PC和智能设备间进行近距离无线通信。NFC提供了一中简单的、非触控式的解决方案，可以让消费者简单直观的交换信息、访问内容与服务。
- NFC整合了非接触式读卡器、非接触式智能卡和点对点通信功能。它的应用场景很广泛，例如：移动支付、位置服务信息、身份识别、公共交通卡等应用。

## 内容介绍
### NFC的三种工作模式
- 读卡器模式（Reader/Writer mode）：作为非接触读卡器使用，比如从海报或者展览信息电子标签上读取相关信息。
- 仿真卡模式（Card emulation mode）：将设备模拟成采用RFID技术的IC卡，进行卡模拟的设备必须自带安全组件，NFC芯片。
- 点对点模式（P2P mode）：和红外线、蓝牙类似于数据交换，在Android Beam之后的Android系统（Android4.0）支持了点对点数据传输。

### Android系统检测NFC流程
- Android设备在开启NFC功能后无论屏幕是否打开都会一直监听设备附近的NFC tag。
- 检测到NFC tag后会解析内部附带的数据：MIME/URI（不同类型的NFC tag所携带的数据也不一样）。
- 将解析到的数据存到Intent中，并通过intent开启应用。

### intent类型
- ACTION_NDEF_DISCOVERED：当tag中附带NDEF数据的时候触发。
- ACTION_TECH_DISCOVERED：当tag中没有检测到NDEF数据，但是其中包含的数据时已知的类型。
- ACTION_TAG_DISCOVERED:如果上面两种都没有检测到，就使用这个启动应用。
- 上面三个action排序优先级从高到底。

### NFC开发中的类
- NfcManager：用来管理Android设备中所有NFC Adapter。因为大部分Android设备只支持一个NFC Adapter，因此可以直接使用getDefaultAdapter来获取系统的AfcAdapter。
- NfcAdapter：手机的NFC硬件设备，更专业的称呼是NFC适配器，该类可以定义一个Intent使系统在检测到NFC tag时通知用户定义的Activity，并提供用来注册Foreground tag消息发送的方法等。
- tag：代表一个被动式NFC对象，比如一张公交卡，Tag可以理解成能被手机NFC读写的对象。当Android检测到一个tag时，会创建一个Tag对象，将其放到Intent对象中，然后发送到相应的Activity。
- IsoDep: 该类位于android.nfc.tech.IsoDep包中，是支持ISO-DEP(ISO 14443-4)协议tag的操作类。NFC协议众多，其他的协议就要用到其他类了，常见的还有：
>1. MifareClassic类：支持MifareClassic协议
>2. MifareUltralight类：支持 Mifare Ultralight协议
>3. Ndef类和DdefFormatable类：支持NDEF格式
>4. NfcA类：支持NFC-A（ISO-14443-3A）协议
>5. NfcB类：支持NFC-B（ISO-14443-3B）协议
>6. NfcF类：支持NFC-F（JIS 6319-4）协议
>7. Nfcv类：支持NFC-V（ISO 15693）协议
>8. 等等

- 我们的二代身份证 用的是NfcB协议，Android 文件分享用的是Ndef格式传输数据。

### NFC调度系统
- NFC调度是指手机检测到NFC对象后如何处理，调度系统分为前台调度系统（Foreground Dispatch System）和标签调度系统（NFC Tag Dispatch System）

#### 前台调度系统
- NFC前台调度系统是一种用于在运行的程序中（前台呈现的Activity）处理tag的技术，即前台调度系统允许Activity拦截Intent对象，并且声明该Activity的优先级比其他的处理Intent对象的Activity高。前台调度系统在一些涉及需要在前台呈现的页面中直接获取或推送NFC信息时十分方便。
- 前台调度的使用方法如下：

``` java
// 创建一个PendingIntent对象，以便Android系统能够在扫描到NFC标签时，用它来封装NFC标签的详细信息
PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
// 创建Intent过滤器
IntentFilter iso = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
// 创建一个处理NFC标签技术的数组
String[][] techLists = new String[][]{new String[]{IsoDep.class.getName()}};
// 在主线程中调用enableForegroundDispatch()方法，一旦NFC标签接触到手机，这个方法就会被激活
adapter.enableForegroundDispatch(this, pendingIntent, new IntentFilter[]{iso}, techLists);

```
#### 标签调度系统
- NFC标签调度系统是一种通过预先定义好的Tag或NDEF消息来启动应用程序的机制，当扫描到一个NFC Tag时，如果Intent中注册对应的App，那么在处理该Tag信息时就会启动该App。当存在多个可以处理该Tag信息的Apps时，系统会弹出一个Activity Choose，供用户选择开启哪个应用。标签调度系统定义了3种Intent对象，按照优先级由高到低分别为ACTION_NDEF_DISCOVERED、ACTION_TECH_DISCOVERED、ACTION_TAB_DISCOVERED（就是上面提到的三种intent）。
- 标签调度的基本工作方法如下：
>1.  用解析NFC标签时由标签调度系统创建的Intent对象（ACTION_NDEF_DISCOVERED）来尝试启动Activity。
>2. 如果没有对应的处理Intent的Activity，就会尝试使用下一个优先级的Intent（ACTION_TECH_DISCOVERED，继而ACTION_TAG_DISCOVERED）来启动Activity，直到有对应的App来处理这个Intent，或者是直接标签调度系统尝试了所有可能的Intent。
>3. 如果没有应用程序来处理任何类型的Intent，就不做任何事情

## NFC基本使用
### 申请权限

``` xml
在AndroidManifest.xml中配置
<uses-permission android:name="android.permission.NFC" />
```

### 申明app具有nfc功能（非必须）
- 下面标签作用：在应用商店显示本app需要手机支持NFC功能，如果NFC功能是非必须的，可以不声明，在代码中通过getDefaultAdapter()的返回值判断手机是否支持NFC。
``` xml
<uses-feature android:name="android.hardware.nfc" android:required="true" />
```

### Filter 设置
#### 例子1：MIME：text/plain
``` xml
<intent-filter>
    <action android:name="android.nfc.action.NDEF_DISCOVERED"/>
        <category android:name="android.intent.category.DEFAULT"/>
    <data android:mimeType="text/plain" />
</intent-filter>
```

#### 例子2：URI:http://developer.android.com/index.html
``` xml
<intent-filter>
    <action android:name="android.nfc.action.NDEF_DISCOVERED"/>
        <category android:name="android.intent.category.DEFAULT"/>
    <data android:scheme="http"
                 android:host="developer.android.com"
             android:pathPrefix="/index.html" />
</intent-filter>
```

### ACTION_TECH_DISCOVERED
- 如果定义了这个action，必须创建一个XML资源文件定义activity所支持的技术.
- /res/xml文件夹下创建资源文件：(例子):
``` xml
<resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
    <tech-list>
            <tech>android.nfc.tech.IsoDep</tech>
            <tech>android.nfc.tech.NfcA</tech>
            <tech>android.nfc.tech.NfcB</tech>
            <tech>android.nfc.tech.NfcF</tech>
            <tech>android.nfc.tech.NfcV</tech>
            <tech>android.nfc.tech.Ndef</tech>
            <tech>android.nfc.tech.NdefFormatable</tech>
            <tech>android.nfc.tech.MifareClassic</tech>
            <tech>android.nfc.tech.MifareUltralight</tech>
        </tech-list>
</resources>

```
- 在AndroidManifest.xml中创建<meta-data>:
``` xml
<activity>
...
<intent-filter>
    <action android:name="android.nfc.action.TECH_DISCOVERED"/>
</intent-filter>

<meta-data android:name="android.nfc.action.TECH_DISCOVERED"
    android:resource="@xml/nfc_tech_filter" />
...
</activity>
```

### ACTION_TAG_DISCOVERED
- 声明这个action，只需要在AndroidMainfest中配置：
``` xml
<intent-filter>
    <action android:name="android.nfc.action.TAG_DISCOVERED"/>
</intent-filter>
```

### 从Intent中获取数据
- intent中可能携带的数据有下面三种，具体哪个里面有数据取决于检测到的NFC tag中的数据类型
>1. EXTRA_TAG:必须
>2. ECTRA_NDEF_MESSAGES:可选
>3. EXTRA_ID:可选

- 获取数据的例子：
``` java
public void onResume() {
    super.onResume();
    ...
    if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        if (rawMsgs != null) {
            msgs = new NdefMessage[rawMsgs.length];
            for (int i = 0; i < rawMsgs.length; i++) {
                msgs[i] = (NdefMessage) rawMsgs[i];
            }
        }
    }
    //process the msgs array
}
```
- 或者：可以从Intent中获取Tag 对象，tag对象中包含了数据和数据类型
``` java
Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
```
### 处理 Tag 对象
- 上面提到了tag有不同的技术协议实现，这里需要转换成对应协议的对象，如下：
``` java
IsoDep isoDep = IsoDep.get(tag);
this.nfcB = NfcB.get((Tag)p);
```
- 获取到该对象后进行I/O流读写，注意读写代码属于耗时操作需要在子线程中执行,要从nfc卡中读取信息，需要发起一个指令才能收到相应的信息，不同的nfc卡的指令不同。

### 一个完整的Demo
- [NFC例子](https://github.com/fangmd/NFC_Demo)
