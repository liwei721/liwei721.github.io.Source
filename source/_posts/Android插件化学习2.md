---
title: Android插件化学习记录（二）
date: 2017-04-18 22:42:05
tags: Android, 插件化
categories: Android技术
---
## 前言
- 接第一篇学习插件化的文章，因为内容太多了，所以就另开了一篇文章来做笔记。

## 插件加载机制
- 前文中最后【启动未在AndroidManifest中声明Activity】的两个Activity是在同一个apk中的，因此用ClassLoader可以加载到对应的类，但是现实中，我们加载的Activity可能是不同插件中的，这时候如果单纯的用ClassLoader去加载，就不知道该去哪里找目标的Activity了，需要对ClassLoader进行改造。

### ClassLoader 机制
- java的类加载是一个相对复杂的过程；它包括加载、验证、准备、解析和初始化五个阶段，对于开发者来说，可控性最强的是加载阶段，加载阶段主要完成三件事：
>1. 根据一个类的全限定名来获取定义此类的二进制字节流
>2. 将这个字节流所代表的静态存储结构转化为JVM方法区中的运行时数据结构
>3. 在内存中生成一个代表这个类的java.lang.Class对象，作为方法区这个类的各种数据的访问入口。

- Android Framework 提供了DexClassLoader这个类，我们只需要告诉DexClassLoader一个dex文件或者apk文件的路径就能完成类的加载。我们再看下Activity穿件过程：
``` java
java.lang.ClassLoader cl = r.packageInfo.getClassLoader();
activity = mInstrumentation.newActivity(cl, component.getClassName(), r.intent);
StrictMode.incrementExpectedActivityCount(activity.getClass());
r.intent.setExtrasClassLoader(cl);
```
- 系统通过待启动的Activity的类名className，然后使用ClassLoader对象cl把这个类加载进虚拟机，最后使用反射创建了这个Activity类的实例对象。
- r.packageInfo是一个LoadedApk类的对象，loadedApk对象是APK文件在内存中的表示，比如：APK文件的代码和资源，甚至代码里面的Activity、service等组件信息，我们可以通过此对象获取。
- 我们接下来看看r.packageInfo是怎么获取的，跟踪代码到getPackageInfoNoCheck，它只是调用了getPackageInfo：

``` file
private LoadedApk getPackageInfo(ApplicationInfo aInfo, CompatibilityInfo compatInfo,
        ClassLoader baseLoader, boolean securityViolation, boolean includeCode,
        boolean registerPackage) {
        // 获取userid信息, 判断了调用方和获取app信息的一方是不是同一个Userid。如果是同一个user，可以共享缓存数据。
    final boolean differentUser = (UserHandle.myUserId() != UserHandle.getUserId(aInfo.uid));
    synchronized (mResourcesManager) {
    // 尝试获取缓存信息
        WeakReference<LoadedApk> ref;
        if (differentUser) {
            // Caching not supported across users
            ref = null;
        } else if (includeCode) {
            ref = mPackages.get(aInfo.packageName);
        } else {
            ref = mResourcePackages.get(aInfo.packageName);
        }

        // 获取缓存数据，如果没有找到缓存数据，才通过LoadedApk的构造函数创建loadedApk对象。
        LoadedApk packageInfo = ref != null ? ref.get() : null;
        if (packageInfo == null || (packageInfo.mResources != null
                && !packageInfo.mResources.getAssets().isUpToDate())) {
                // 缓存没有命中，直接new
            packageInfo =
                new LoadedApk(this, aInfo, compatInfo, baseLoader,
                        securityViolation, includeCode &&
                        (aInfo.flags&ApplicationInfo.FLAG_HAS_CODE) != 0, registerPackage);

        // 省略。。更新缓存
        return packageInfo;
    }
}
```
### Hook掉ClassLoader(自己操刀)
- 上面提到，获取LoadedApk的过程中使用了一份缓存数据，这个缓存数据是一个Map（mPackages），从包名到LoadedApk的一个映射，我们可以考虑手动把我们插件信息添加进去。这样系统查找缓存的过程中，会直接命中缓存，进而使用我们添加进去的LoadedApk的ClassLoader来加载这个特定的Activity。
- 我们按照下面的步骤来进行：
> 1. 我们的主要目标是：Hook获取ActivityThread中的mpackages。那么下一步我们需要去构造一个插件的loadedApk。
> 2. 通过getPackageInfoNoCheck来构造一个插件的LoadedApk对象。public final LoadedApk getPackageInfoNoCheck(ApplicationInfo ai,CompatibilityInfo compatInfo) {}， 第二个参数是兼容性问题，我们使用默认的值DEFAULT_COMPATIBILITY_INFO，接下来主要是hi获取ApplicationInfo。
>3. 绕过权限限制，如果按照前两步完成配置运行会报错： Unable to instantiate application android.app.Application：Unable to get package info for com.weishu.upf.ams_pms_hook.app; is package not installed?  跟踪代码会发现其实是在读取packageInfo时，因为插件并没有安装，所以抛出了异常，因此我们还需要hook PMS，欺骗系统我们已经安装了插件。

## 委托系统，让系统帮忙加载
- 再看ActivityThread中加载Activity的代码：

``` java
java.lang.ClassLoader cl = r.packageInfo.getClassLoader();
activity = mInstrumentation.newActivity(
        cl, component.getClassName(), r.intent);
StrictMode.incrementExpectedActivityCount(activity.getClass());
r.intent.setExtrasClassLoader(cl);

```
- 上面提到r.packageInfo中的r是通过getPackageInfoNoCheck获取的，如果在mPackages中没有loadedApk，那么系统会new 一个loadedApk。
- 接下来会使用这个new出来的loadedApk的getClassLoader方法获取到的ClassLoader来加载类，这里的ClassLoader就是宿主的ClassLoader，因此还无法加载插件的类，我们可以通过告诉宿主程序的ClassLoader插件使用的类，让宿主ClassLoader完成对子类的加载。
- 我们来看下LoadedApk.getClassLoader是个什么东西：
``` java
public ClassLoader getClassLoader() {
    synchronized (this) {
        if (mClassLoader != null) {
            return mClassLoader;
        }

        if (mIncludeCode && !mPackageName.equals("android")) {
            // 略...
            mClassLoader = ApplicationLoaders.getDefault().getClassLoader(zip, lib,
                    mBaseClassLoader);

            StrictMode.setThreadPolicy(oldPolicy);
        } else {
            if (mBaseClassLoader == null) {
                mClassLoader = ClassLoader.getSystemClassLoader();
            } else {
                mClassLoader = mBaseClassLoader;
            }
        }
        return mClassLoader;
    }
}
```
- 可以看到，非android开头的包和android开头的包分别使用了两种不同的ClassLoader，我们只关心第一种；因此继续跟踪ApplicationLoaders类：

``` java
public ClassLoader getClassLoader(String zip, String libPath, ClassLoader parent)
{

    ClassLoader baseParent = ClassLoader.getSystemClassLoader().getParent();

    synchronized (mLoaders) {
        if (parent == null) {
            parent = baseParent;
        }

        if (parent == baseParent) {
            ClassLoader loader = mLoaders.get(zip);
            if (loader != null) {
                return loader;
            }

            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, zip);
            PathClassLoader pathClassloader =
                new PathClassLoader(zip, libPath, parent);
            Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);

            mLoaders.put(zip, pathClassloader);
            return pathClassloader;
        }

        Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, zip);
        PathClassLoader pathClassloader = new PathClassLoader(zip, parent);
        Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
        return pathClassloader;
    }
}
```
- 应用程序使用的ClassLoader都是PathClassLoader类的实例

``` java
public class PathClassLoader extends BaseDexClassLoader {
    public PathClassLoader(String dexPath, ClassLoader parent) {
        super((String)null, (File)null, (String)null, (ClassLoader)null);
        throw new RuntimeException("Stub!");
    }

    public PathClassLoader(String dexPath, String libraryPath, ClassLoader parent) {
        super((String)null, (File)null, (String)null, (ClassLoader)null);
        throw new RuntimeException("Stub!");
    }
}
```
- 它的实现比较简单，我们再看BaseDexClassLoader

``` java
protected Class<?> findClass(String name) throws ClassNotFoundException {
    List<Throwable> suppressedExceptions = new ArrayList<Throwable>();
    // 通过pathList的findClass来查找对应的class。pathList其实是一个DexPathList对象。
    Class c = pathList.findClass(name, suppressedExceptions);
    if (c == null) {
        ClassNotFoundException cnfe = new ClassNotFoundException("Didn't find class \"" + name + "\" on path: " + pathList);
        for (Throwable t : suppressedExceptions) {
            cnfe.addSuppressed(t);
        }
        throw cnfe;
    }
    return c;
}
```

``` java
public Class findClass(String name, List<Throwable> suppressed) {
  // dexElements是一个Element的数组，ClassLoader在查找类的时候，会遍历这个数组。
   for (Element element : dexElements) {
       DexFile dex = element.dexFile;

       if (dex != null) {
           Class clazz = dex.loadClassBinaryName(name, definingContext, suppressed);
           if (clazz != null) {
               return clazz;
           }
       }
   }
   if (dexElementsSuppressedExceptions != null) {
       suppressed.addAll(Arrays.asList(dexElementsSuppressedExceptions));
   }
   return null;
}
```
- 总的来说，宿主程序的ClassLoader最终继承自BaseDexClassLoader，BaseDexClassLoader通过DexPathList进行类的查找过程，而这个查找通过遍历一个dexElements的数组完成，我们通过把插件dex添加进这个数组让宿主ClassLoader获取了加载插件类的能力。

## 广播的管理
### 注册广播
- 我们先来分析动态注册广播的方法，使用时会调用Context.registerReceiver,跟踪代码，最终会调用到registerReceiverInternal中：

``` java
private Intent registerReceiverInternal(BroadcastReceiver receiver, int userId,
            IntentFilter filter, String broadcastPermission,
            Handler scheduler, Context context) {
        IIntentReceiver rd = null;
        if (receiver != null) {
            if (mPackageInfo != null && context != null) {
                if (scheduler == null) {
                    scheduler = mMainThread.getHandler();
                }
                rd = mPackageInfo.getReceiverDispatcher(
                    receiver, context, scheduler,
                    mMainThread.getInstrumentation(), true);
            } else {
                if (scheduler == null) {
                    scheduler = mMainThread.getHandler();
                }
                rd = new LoadedApk.ReceiverDispatcher(
                        receiver, context, scheduler, null, true).getIIntentReceiver();
            }
        }
        try {
            return ActivityManagerNative.getDefault().registerReceiver(
                    mMainThread.getApplicationThread(), mBasePackageName,
                    rd, filter, broadcastPermission, userId);
        } catch (RemoteException e) {
            return null;
        }
    }
```
- 从代码可以知道，BroadcastReceiver的注册也是通过AMS完成的。
- IIntentReceiver是一个Binder对象，因此是可以跨进程通信的，AMS和BroadcastReceiver的通信是靠IIntentReceiver完成的，这是因为广播的分发是在AMS中进行的，而AMS所在的进程和BroadCastReceiver所在的进程不一样，因此要把广播分发到BroadcastReceiver具体的进程需要跨进程通信。IIntentReceiver的实现类是LoadedApk.ReceiverDispatcher。
- AMS registerReceiver方法主要做了两件事情：
>1. 对发送者的身份和权限做一定的校验。
>2. 把这个BroadcastReceiver以BroadcastFilter的形式存储在AMS的mReceiverResolver变量中，供后续使用。

- 静态注册广播的信息，我们从前面介绍构建applicationInfo时知道，PackageParser类会对AndroidManifest.xml文件解析，因为APK的解析过程是在PMS中进行的，因此静态注册广播的信息存储在PMS中。

### 发送过程
- 发送广播很简单，context.sendBroadcast(), 跟踪代码到ContextImpl中：

``` java
public void sendBroadcast(Intent intent) {
    warnIfCallingFromSystemProcess();
    String resolvedType = intent.resolveTypeIfNeeded(getContentResolver());
    try {
        intent.prepareToLeaveProcess();
        ActivityManagerNative.getDefault().broadcastIntent(
                mMainThread.getApplicationThread(), intent, resolvedType, null,
                Activity.RESULT_OK, null, null, null, AppOpsManager.OP_NONE, null, false, false,
                getUserId());
    } catch (RemoteException e) {
        throw new RuntimeException("Failure from system", e);
    }
}
```
- 我们跟踪进AMS的broadcastIntent()方法，它仅仅是调用了broadcastIntentLocked方法，这个方法也比较长，大概是：处理了粘性广播、顺序广播，各种flag以及动态广播静态广播的接收过程，这里就可以看出广播的发送和接收是混为一体的。某个广播被发送之后，AMS会找出所有注册过的BroadcastReceiver中与这个广播匹配的接收者，然后将这个广播分发给相应的接收者处理。

#### 匹配过程。
- 上面提到broadcastIntentLocked方法里也有接收广播的代码：

``` file
// Figure out who all will receive this broadcast.
List receivers = null;
List<BroadcastFilter> registeredReceivers = null;
// Need to resolve the intent to interested receivers...
if ((intent.getFlags()&Intent.FLAG_RECEIVER_REGISTERED_ONLY)
         == 0) {
    receivers = collectReceiverComponents(intent, resolvedType, callingUid, users);
}
if (intent.getComponent() == null) {
    if (userId == UserHandle.USER_ALL && callingUid == Process.SHELL_UID) {
        // Query one target user at a time, excluding shell-restricted users
        // 略
    } else {
        registeredReceivers = mReceiverResolver.queryIntent(intent,
                resolvedType, false, userId);
    }
}
```
- receivers是对这个广播感兴趣的静态BroadcastReceiver列表；collectReceiverComponents 通过PackageManager获取了与这个广播匹配的静态BroadcastReceiver信息；这里也证实了我们在分析BroadcasrReceiver注册过程中的推论——静态BroadcastReceiver的注册过程的确实在PMS中进行的。
- mReceiverResolver存储了动态注册的BroadcastReceiver的信息；还记得这个mReceiverResolver吗？我们在分析动态广播的注册过程中发现，动态注册的BroadcastReceiver的相关信息最终存储在此对象之中；在这里，通过mReceiverResolver对象匹配出了对应的BroadcastReceiver供进一步使用。

### 接收过程
- 还是上面broadcastIntentLocked方法中的接收代码：

``` java
BroadcastQueue queue = broadcastQueueForIntent(intent);
BroadcastRecord r = new BroadcastRecord(queue, intent, callerApp,
        callerPackage, callingPid, callingUid, resolvedType,
        requiredPermissions, appOp, brOptions, receivers, resultTo, resultCode,
        resultData, resultExtras, ordered, sticky, false, userId);

boolean replaced = replacePending && queue.replaceOrderedBroadcastLocked(r);
if (!replaced) {
    queue.enqueueOrderedBroadcastLocked(r);
    queue.scheduleBroadcastsLocked();
}
```
- 首先创建一个BroadcastRecord代表此次发送的广播，然后放进一个队列。最后通过scheduleBroadcastsLocked通知队列对广播进行处理。
- 在BroadcastQueue中通过Handle调度了对于广播处理的消息，调度过程由processNextBroadcast方法完成，而这个方法通过performReceiveLocked最终调用了IIntentReceiver的performReceive方法。
- 这个IIntentReceiver正是在广播注册过程中由App进程提供给AMS进程的Binder对象，现在AMS通过这个Binder对象进行IPC调用通知广播接受者所在进程完成余下操作。在上文我们分析广播的注册过程中提到过，这个IItentReceiver的实现是LoadedApk.ReceiverDispatcher；我们查看这个对象的performReceive方法，源码如下：

``` java
public void performReceive(Intent intent, int resultCode, String data,
        Bundle extras, boolean ordered, boolean sticky, int sendingUser) {
    Args args = new Args(intent, resultCode, data, extras, ordered,
            sticky, sendingUser);
    if (!mActivityThread.post(args)) {
        if (mRegistered && ordered) {
            IActivityManager mgr = ActivityManagerNative.getDefault();
            args.sendFinished(mgr);
        }
    }
}
```

- 这个方法创建了一个Args对象，然后把它post到了mActivityThread这个Handler中；我们查看Args类的run方法:

``` java
public void run() {
    final BroadcastReceiver receiver = mReceiver;
    final boolean ordered = mOrdered;  
    final IActivityManager mgr = ActivityManagerNative.getDefault();
    final Intent intent = mCurIntent;
    mCurIntent = null;

    if (receiver == null || mForgotten) {
        if (mRegistered && ordered) {
            sendFinished(mgr);
        }
        return;
    }

    try {
        ClassLoader cl =  mReceiver.getClass().getClassLoader(); // Important!! load class
        intent.setExtrasClassLoader(cl);
        setExtrasClassLoader(cl);
        receiver.setPendingResult(this);
        receiver.onReceive(mContext, intent); // callback
    } catch (Exception e) {
        if (mRegistered && ordered) {
            sendFinished(mgr);
        }
        if (mInstrumentation == null ||
                !mInstrumentation.onException(mReceiver, e)) {
            throw new RuntimeException(
                "Error receiving broadcast " + intent
                + " in " + mReceiver, e);
        }
    }

    if (receiver.getPendingResult() != null) {
        finish();
    }
}
```
- 这里就是回调onReceive的地方，看到我们比较熟悉的方法了，哈哈。不过从这里也可以看出来，在onReceive中不能做耗时的操作，因为被post到了主线程。

### receiver的hook
- 动态注册的receiver相对比较好hook，因为不需要在AndroidManifest中配置，我们可以在ClassLoader中进行处理，将Receiver加载进来，然后想办法让receiver收到onReceive回调。
- 主要是静态注册比较恶心，因为在AndroidManifest中配置之后，我们不能用一个假的Receiver替代，因为在receiver中还会有intentfilter，我们无法知道该给虚假的receiver添加什么intentFilter，因此我们想到一种方案是，将静态广播当做动态广播处理，唯一的影响是静态广播在程序死掉之后还能收到广播。
- 主要的思路是通过PackageParse解析apk，获取AndroidManifest中注册的广播，然后再手动的动态注册。
- 主要的代码如下所示：

``` files
// 获取packageParser
           Class<?> packageParserClass = Class.forName("android.content.pm.PackageParser");
           Method parsePackageMethod = packageParserClass.getDeclaredMethod("parsePackage", File.class, int.class);
           Object packageParser = packageParserClass.newInstance();

           // 首先调用parsePackage获取到apk对象对应的Package对象。
           Object packageObj = parsePackageMethod.invoke(packageParser, apkFile, PackageManager.GET_RECEIVERS);

           // 读取Package对象里面的receivers字段，注意这里是一个List<Activity>
           // 接下来要做的就是根据这个List<Activity>获取到Receiver对应的ActivityInfo。
           Field receiversField = packageObj.getClass().getDeclaredField("receivers");
           receiversField.setAccessible(true);
           List receivers = (List) receiversField.get(packageObj);

           Class<?> activityClass = Class.forName("android.content.pm.PackageParser$Activity");
           Field infoField = activityClass.getDeclaredField("info");
           infoField.setAccessible(true);


           Class<?> componentClass = Class.forName("android.content.pm.PackageParser$Component");
           Field intentsField = componentClass.getDeclaredField("intents");


           // 解析出receiver以及对应的 intentFilter
           for (Object receiver: receivers){
//                ActivityInfo info = (ActivityInfo) generateReceiverInfo.invoke(packageParser, receiver, 0, defaultUserState, userId);
               ActivityInfo info = (ActivityInfo) infoField.get(receiver);
               List<? extends IntentFilter> filters = (List<? extends IntentFilter>) intentsField.get(receiver);
               sCache.put(info, filters);
           }

           ClassLoader cl = null;
        for (ActivityInfo activityInfo : sCache.keySet()){
            List<? extends IntentFilter> intentFilters = sCache.get(activityInfo);

            // 获取插件的ClassLoader
            if (cl == null){
                cl = CustomClassLoader.getPluginClassLoader(apk, activityInfo.packageName);
            }

            // 解析出来的每一个静态Receiver都注册为动态的。
            for (IntentFilter intentFilter : intentFilters){
                BroadcastReceiver receiver = (BroadcastReceiver) cl.loadClass(activityInfo.name).newInstance();
                context.registerReceiver(receiver, intentFilter);
            }
        }
```

- 动态广播的插件化待研究实现。
