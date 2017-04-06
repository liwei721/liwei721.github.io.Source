---
title: Android对内存的测试
date: 2017-03-16 13:43:13
tags: 内存，LeakCanary
categories: Android性能测试
---

## 背景
- 内存对移动设备来说是非常宝贵的资源，因为每个app可以使用的内存是有限的，从最开始的16M、32M到现在的128M，虽然随着硬件成本的降低，内存在增大，但是我们还是会经常碰到OOM。
- OOM其实只是一个表象，更深层次的原因是内存泄露。因为对内存的测试，是我们完善产品质量的一个重要环节。

## 测试内容
- 对内存的测试我们主要关注两个方面：**内存泄露** 和 **内存抖动**
- 内存泄露的主要影响是上面提到的：容易造成OOM影响app产品体验，且间接的会造成频繁GC。
- 内存抖动的原因是：频繁的GC（所以内存会短时间内增加或降低），造成的影响是:界面在使用时比较卡顿，因为GC会导致JVM暂停执行你的任务进行内存回收。
- 产生内存抖动的代码层面的原因是：
  >1. 在循环中创建对象，产生内存碎片。
  >2. 瞬间创建比较大的对象。
  >3. 内存泄露也是一方面原因。

## 测试方法

### 内存泄露
- 之前制定的文档中，准备采用纯手工的方式去测，但是实践了一段时间，发现手工测试工作量大且对于小量的内存泄露不太好发现。于是就尝试了用LeakCanary工具。
- LeakCanary确实比较强大，甚至可以发现系统的一些泄露，比如我在测试自己app的时候，调整了音量，然后LeakCanary报了一个mediasessionlegacyhelper的内存泄露，其实这个泄露是SDK自身的泄露，当使用它mediasessionlegacyhelper.getHelper(context),传入context后因为其内部是静态变量持有了传入的context，所以造成泄露。
- 随后决定之后内存泄露用LeakCanary。

#### LeakCanary改良版
- 使用LeakCanary手动测试，它自带一个展示页面DisplayActivity，测试过程中发现的泄露都会展示在这里。但是如果我们想通过跑自动化的方式来收集内存泄露信息会碰到如下问题：
> 1. 跑monkey时会经常进到DisplayActivity中，影响测试。
> 2. 跑monkey时，如果进入到DisplayActivity中，monkey有可能会点击删除已经发现的泄露。
> 3. 如果有UI自动化的话，上面两个问题倒不存在。

- 为了能跑自动化，我们要对LeakCanary改良下。主要是两方面的改动：
- 在app的自定义Application的onCreate方法中：
``` java
      RefWatcher refWatcher;
//        refWatcher = LeakCanary.install(this);   // 没有改良之前，是这么用的，直接install(this)
       refWatcher = LeakCanary.install(this, LeakUploadService.class, AndroidExcludedRefs.createAndroidDefaults().build());
       refWatcher.watch(this);
       LeakCanaryInternals.setEnabled(this, DisplayLeakActivity.class, false);  //这句代码的意思是不显示DisplayActivity，将false改成true就可以看见DisplayActivity。
```
- 自定义一个Service用于处理采集到的泄露数据，我这里是直接保存到sdcard中，也可以上传到服务端数据库中。可以新建一个leakcanary包，将下面的service代码放到包中。
``` java
import android.os.Environment;
import android.util.Log;

import com.squareup.leakcanary.AnalysisResult;
import com.squareup.leakcanary.DisplayLeakService;
import com.squareup.leakcanary.HeapDump;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Created by zlw on 2016/12/7.
 */

public class LeakUploadService extends DisplayLeakService {
    private final static String TAG = LeakUploadService.class.getSimpleName();
    private String LocalFileName = "LeakCanary.log";

    @Override
    protected void afterDefaultHandling(HeapDump heapDump, AnalysisResult result, String leakInfo) {
        if (!result.leakFound || result.excludedLeak) return;
        // 将LeakCanary信息写入本地
        writeToFile(leakInfo);

        Log.d(TAG, "leakInfo = " + leakInfo);
    }

    /**
     *  将采集的泄露数据写入本地
     * @param leakInfo
     */
    private void writeToFile(String leakInfo){
        if (leakInfo == null || "".equals(leakInfo)) return;

        String sdcardPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        File leakFolder = new File(sdcardPath + "/leakcanary/");
        File leakFile = null;
        if (!leakFolder.exists()){
            boolean suc = leakFolder.mkdirs();
            if (!suc){
                return;
            }

        }else {
            leakFile = new File(sdcardPath + "/leakcanary/" + LocalFileName);
            if (!leakFile.exists()){
                try {
                    boolean suc1= leakFile.createNewFile();
                    if (!suc1) return;
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }

        BufferedWriter bw = null;
        if (leakFile == null){
            return;
        }
        try{
            bw = new BufferedWriter(new FileWriter(leakFile, true));
            bw.write(leakInfo);

            bw.write("=================================================");
            bw.newLine();
            bw.flush();
        }catch (Exception ex){
            ex.printStackTrace();

        }finally {
            try{
                if (bw != null){
                    bw.close();
                }

            }catch (IOException e){
                e.printStackTrace();
            }
        }

    }
}

```
- 接下来需要在AndroidMainfest.xml中配置Service，并检查app有没有读写sdcard的权限
``` xml
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />

    <!--leakcanary service begin-->
       <service android:name=".leakcanary.LeakUploadService" android:exported="false"/>
       <!--leakcanary service end-->
```
- 这样就大功告成了，上面唯一的不足是需要源码，并能将源码编译通过。我暂时没想到更好的解决办法。

#### UI自动化
- 如果已经有现成的UI自动化脚本最好，直接拿来跑就完了，比如我们有appium脚本。
- 如果没有UI自动化脚本，只能使用monkey了，我这里提供一个monkey，仅空参考(我主要是屏蔽了点击物理按键)：
``` bash
adb shell monkey -p com.XXX.XXXX --pct-touch 30 --pct-motion 20 --pct-appswitch 50 --pct-rotation 0 --pct-syskeys 0 --pct-nav 0 -s 12358 --throttle 400 --ignore-crashes --ignore-timeouts -v 9000
```

#### 其他
- 还有一个问题，就是monkey容易点通知栏，有可能会将网络关掉，这个有大牛已经解决了，详情见[simiasque](https://github.com/Orange-OpenSource/simiasque)。这是一个apk，我们可以提前装到手机上。
- simiasque 打开状态可以屏蔽状态栏，这样monkey就点不到通知栏了。另外它还提供了命令行打开和关闭模式，不过前提是得打开app。结合之前的monkey命令，我们可以写个批处理：
``` bash
adb shell am broadcast -a org.thisisafactory.simiasque.SET_OVERLAY --ez enable true

adb shell monkey -p com.xxx.xxxxx --pct-touch 30 --pct-motion 20 --pct-appswitch 50 --pct-rotation 0 --pct-syskeys 0 --pct-nav 0 -s 12358 --throttle 400 --ignore-crashes --ignore-timeouts -v 9000

adb shell am broadcast -a org.thisisafactory.simiasque.SET_OVERLAY --ez enable false
```
#### 结果分析
- 测试完成后，可以去/sdcard/leakcanary/目录下看是否生成LeakCanary.log， 如果没有生成说明没有内存泄露。
- 如果有内存泄露，我们将LeakCanary.log 用adb 命令pull到本地：
``` bash
adb pull /sdcard/leakcanary/LeakCanary.log
```
- 建议用nodepad++或者Sublime Text等文本工具打开，格式支持更好一些。
- 我们举个例子，如下所示：
``` java
In com.XXX.XXXXXX:3.1.0.4653:3104653.
* com.XXXX.XXXXX.application.XXXXX has leaked:
* GC ROOT static com.raizlabs.android.dbflow.config.FlowManager.config
* references com.raizlabs.android.dbflow.config.FlowConfig.context
* leaks com.XXXX.XXXXX.application.XXXXX instance

* Retaining: 288 B.
* Reference Key: a3f8a717-8de8-4149-8431-ce6c6e59e373
* Device: XDJA ACTOMA ACTOMA ACE XDJA_SCM
* Android Version: 5.0.2 API: 21 LeakCanary: 1.4-beta2 3799172
* Durations: watch=6673ms, gc=166ms, heap dump=1168ms, analysis=425892ms

* Details:
* Class com.raizlabs.android.dbflow.config.FlowManager
|   static DEFAULT_DATABASE_HOLDER_NAME = java.lang.String@316030112 (0x12d63ca0)
|   static config = com.raizlabs.android.dbflow.config.FlowConfig@316030048 (0x12d63c60)
|   static DEFAULT_DATABASE_HOLDER_CLASSNAME = java.lang.String@316030432 (0x12d63de0)
|   static $staticOverhead = byte[48]@314895937 (0x12c4ee41)
|   static globalDatabaseHolder = com.raizlabs.android.dbflow.config.FlowManager$GlobalDatabaseHolder@316030208 (0x12d63d00)
|   static DEFAULT_DATABASE_HOLDER_PACKAGE_NAME = java.lang.String@316030272 (0x12d63d40)
|   static loadedModules = java.util.HashSet@316098192 (0x12d74690)
* Instance of com.raizlabs.android.dbflow.config.FlowConfig
|   context = com.XXXX.XXXXX.application.XXXXXXX@314752224 (0x12c2bce0)
|   databaseConfigMap = java.util.HashMap@316020144 (0x12d615b0)
|   databaseHolders = java.util.Collections$UnmodifiableSet@316098144 (0x12d74660)
|   openDatabasesOnInit = false
* Instance of com.XXXX.XXXXXX.application.XXXXXXX
|   static STOP_DEB_LOCKING = false
|   static $staticOverhead = byte[16]@315848705 (0x12d37801)
|   static SCREEN_BROADCAST = java.lang.String@315978240 (0x12d57200)
|   appComponent = com.xdja.HDSafeEMailClient.di.components.DaggerAppComponent@316306176 (0x12da7300)
|   isPauseActivity = java.lang.String@318262688 (0x12f84da0)
|   mScreenReceiver = com.XXXX.XXXXXX.application.XXXXXXXX$ScreenBroadcastReceiver@314600064 (0x12c06a80)
|   onResumeTime = 1489630302358
|   screenLockTime = 0
|   isBackGrounded = false
|   isScreenLocked = false
|   isSetPattern = false
|   applicationComponent = com.XXXX.frame.di.components.DaggerApplicationComponent@314881856 (0x12c4b740)
|   loggable = true
|   applicationLifeCycles = null
|   components = java.util.HashMap@315022016 (0x12c6dac0)
|   mActivityLifecycleCallbacks = java.util.ArrayList@315978304 (0x12d57240)
|   mAssistCallbacks = null
|   mComponentCallbacks = java.util.ArrayList@315978272 (0x12d57220)
|   mLoadedApk = android.app.LoadedApk@314737664 (0x12c28400)
|   mBase = android.app.ContextImpl@315114752 (0x12c84500)
* Excluded Refs:
| Field: android.view.Choreographer$FrameDisplayEventReceiver.mMessageQueue (always)
| Thread:FinalizerWatchdogDaemon (always)
| Thread:main (always)
| Thread:LeakCanary-Heap-Dump (always)
| Class:java.lang.ref.WeakReference (always)
| Class:java.lang.ref.SoftReference (always)
| Class:java.lang.ref.PhantomReference (always)
| Class:java.lang.ref.Finalizer (always)
| Class:java.lang.ref.FinalizerReference (always)
| Root Class:android.os.Binder (always)
```
- 如上所示，我们分以下几步分析问题：
>1. 通过 com.XXXX.XXXXX.application.XXXXX has leaked:  我们知道com.XXXX.XXXXX.application.XXXXX发生了内存泄露。
>2. 接下来我们看Retaining: 288 B    知道大致泄露了288B。有时候泄露比较小时，比如本例中的288B，可以适当放宽处理。
>3. 再来就是分析泄露原因，LeakCanary这一点做的很好，我们直接看Details就好了：Details内容最上面的Class是GC Roots，本例就是：com.raizlabs.android.dbflow.config.FlowConfig@316030048 (0x12d63c60)。所以我们应该从下往上找，一般下面的类是跟我们关系比较大的类。
>4. 本例我们可以得出 com.XXXX.XXXXXX.application.XXXXXXX  以 context传入 com.raizlabs.android.dbflow.config.FlowConfig， 然后com.raizlabs.android.dbflow.config.FlowConfig又以static config的形式存在于com.raizlabs.android.dbflow.config.FlowManager，这样就引起了内存泄露。
>5. 非常好分析吧，LeakCanary比MAT工具分析起来要容易一些。

#### 手动测试
- 当然你可能担心自动化覆盖不全你的用例，你也可以手动去操作。
- 获取数据和分析数据的方式和上面都是一样的。

### 内存抖动
- 内存抖动的跟踪，有两种方式：AndroidStudio monitor和自己写脚本分析。

#### AndroidStudio monitor
- 这是我比较推荐的方式，因为图形的方式，观察抖动更直接。如下图所示：
![内存抖动](/upload/image/zlw/内存抖动.PNG)
- 另外如果有抖动发生，可以直接抓取一个Memory Allocation来查看内存分配情况。
- 建议测内存泄露时，可以同时检测内存抖动。

#### 自己写脚本
- 可以自己写脚本收集抖动数据，这需要一个判断标准，我目标的标准是：10s内抖动4次。
- 这种方式用于我们跑自动化，没有人参与的情况。
- 我们需要记录发生抖动的页面或者截图，方便日后场景重现分析问题。


## 总结
- 内存对性能测试来说是非常重要的，因此需要花更多的时间去测试和验证。
- 对于测试过程中发现的问题，最好找到根本原因，这样可以提高问题解决的效率。
