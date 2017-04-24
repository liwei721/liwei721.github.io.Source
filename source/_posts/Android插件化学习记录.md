---
title: Android插件化学习记录（一）
date: 2017-04-18 22:42:04
tags: Android, 插件化
categories: Android技术
---

## 背景
- 插件化可以解决两种问题：
>1. 从技术上讲，业务逻辑的复杂导致代码量急剧膨胀，各大厂商陆续碰到了65535方法数的限制。
>2. 在业务层面上，功能模块的解耦以及团队模块分工，每个模块能够单独的发布升级，提升产品迭代效率。

- 插件化的实现，从本质上要解决两个问题：1、代码加载   2、资源加载
### 代码加载
- 类的加载可以使用JAVA的ClassLoader机制，但是对于Android来说，并不是类加载进来就可以使用了，Android中很多组件都是有生命的，所以我们还应该考虑组件生命周期管理。
- 另外，如何管理加载进来的类也是一个问题。假设多个插件依赖了相同的类，是抽取公共依赖进行管理还是插件单独依赖？这就是ClassLoader的管理问题；

### 资源加载
- 资源加载方案大家使用的原理都差不多，都是用AssetManager的隐藏方法addAssetPath。
- 但是，不同插件的资源如何管理？是公用一套资源还是插件独立资源？共用资源如何避免资源冲突？对于资源加载，有的方案共用一套资源并采用资源分段机制解决冲突（要么修改aapt要么添加编译插件）；有的方案选择独立资源，不同插件管理自己的资源。

## Hook机制之动态代理
### 动态代理

- JDK提供了动态代理方式，可以简单理解为JVM可以在运行时动态生成一系列的代理类。

``` java
/***
*  @param loader 　一个ClassLoader对象，定义了由哪个ClassLoader对象来对生成的代理对象进行加载
*  @param interfaces 一个Interface对象的数组，表示的是我将要给我需要代理的对象提供一组什么接口，如果我提供了一组接口给它，那么这个代理对象就宣称实现了该接口(多态)，这样我就能调用这组接口中的方法了
* @param 一个InvocationHandler对象，表示的是当我这个动态代理对象在调用方法的时候，会关联到哪一个InvocationHandler对象上
*
**/
public static Object newProxyInstance(ClassLoader loader, Class<?>[] interfaces,  InvocationHandler h)  throws IllegalArgumentException
```
- 上面提到了Invocationhandler，每一个动态代理类都必须要实现InvocationHandler这个接口，并且每个代理类的实例都关联到了一个handler，当我们通过代理对象调用一个方法的时候，这个方法的调用就会被转发由InvocationHandler这个接口的invoke方法来调用：

``` java
Object invoke(Object proxy, Method method, Object[] args) throws Throwable
proxy:   指代我们所代理的真实对象
method:  指代的是我们所要调用真实对象的某个方法的method对象。
args：   指代的是调用真实对象某个方法时所接受的参数。
```

### 代理Hook
- 我们首先创建自己的代理对象（可以实现自己的逻辑），然后把原始对象替换为我们的代理对象，这样我们就能通过这个代理对象为所欲为了（比如：修改参数、替换返回值），这种方式我们称为Hook。
- 找被Hook的对象：静态变量和单例比较容易被Hook，因为在一个进程内，静态变量和单例变量时相对不容易发生变化的。

### hook的流程总结
- 寻找Hook点，原则是静态变量或单例对象，尽量Hook public的对象和方法，非public不保证每个版本都一样，需要适配。
- 选择合适的代理方式，如果是接口可以用动态代理，如果是类可以手动写代理也可以使用cglib。
- 偷梁换柱——用代理对象替换原始对象。

## Hook机制之Binder Hook

### 定义
- Android系统通过Binder机制给应用程序提供了一些列的系统服务，比如：ActivityManagerService、ClipboardManager、AudioManager等，这些系统服务给应用程序提供了强大的功能。
- 如果我们能够Hook住service，那么就可以做一些我们期望的事情，我们把Hook系统服务的机制称之为Binder Hook。

## 寻找Hook点
- 系统service的使用其实分为两步：
>1. IBinder b = ServiceManager.getService("service_name"); // 获取原始的IBinder对象
>2. IXXInterface in = IXXInterface.Stub.asInterface(b); // 转换为Service接口

- 由于系统服务的使用者都是对IXXInterface进行操作，我们只需要把asInterface方法返回的对象修改为我们Hook过的对象。

### asInterface过程
- 以android.content.IClipboard,IClipboard.Stub.asInterface方法为例：

``` java
public static android.content.IClipboard asInterface(android.os.IBinder obj) {
    if ((obj == null)) {
        return null;
    }
    android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR); // Hook点
    if (((iin != null) && (iin instanceof android.content.IClipboard))) {
        return ((android.content.IClipboard) iin);
    }
    return new android.content.IClipboard.Stub.Proxy(obj);
}
```
- 这个方法的步骤:先查看本进程是否存在这个Binder对象，如果有那么直接就是本进程调用了，如果不存在就创建一个代理对象，让代理对象委托驱动完成跨进程调用。
- 我们能hook的点是obj.queryLocalInterface。这里的obj就是我们得到的IBinder对象。

### getService过程

``` java
IBinder b = ServiceManager.getService("service_name");
```
- 根据上面的描述，我们希望修改这个getService方法的返回值，让这个方法返回一个我们伪造过的IBinder对象，这样，我们可以在自己伪造的IBinder对象的queryLocalInterface方法做处理，进而使得asInterface方法返回在queryLocalInterface方法里面处理过的值。最终实现hook系统服务的目的。

### 总结Hook Binder的过程
- 首先肯定需要伪造一个系统服务对象，接下来就要想办法让asInterface能够返回我们的这个伪造对象而不是原始的系统服务对象。
- 通过上文分析我们知道，只要让getService返回IBinder对象的queryLocalInterface方法直接返回我们伪造过的系统服务对象就能达到目的。所以，我们需要伪造一个IBinder对象，主要是修改它的queryLocalInterface方法，让它返回我们伪造的系统服务对象；然后把这个伪造对象放置在ServiceManager的缓存map里面即可。

## Hook机制之AMS&PMS
- ActivityManagerService对于FrameWork层很重要，Android四大组件无一不与它打交道：
>1. startActivity最终调用了AMS的startActivity系列方法，实现了Activity的启动，Activity的声明周期回调，也在AMS中完成。
>2. startService , bindService最终调用到AMS的startService和bindService方法。
>3. 动态广播的注册和接收在AMS中完成（静态广播注册在PMS中完成）
>4. getContentResolver最终从AMS的getContentProvider获取到ContentProvider。

- PackageManagerService则完成了诸如权限校验（checkPermission、checkUidPermission）、Apk meta信息获取（getApplicationInfo等）、四大组件信息获取（query系列方法）等重要功能。

### AMS获取过程
- startActivity有两种形式：
>1. 直接调用Context类的startActivity方法，这种方式启动的Activity没有Activity栈，因此不能以standard方式启动，必须加上FLAG_ACTIVITY_NEW_TASK这个Flag。
>2. 调用被Activity类重载过的startActivity方法，通常在我们的Activity中直接调用这个方法就是这种形式；

``` java
// ContextImpl类
public void startActivity(Intent intent, Bundle options) {
    warnIfCallingFromSystemProcess();
    if ((intent.getFlags()&Intent.FLAG_ACTIVITY_NEW_TASK) == 0) {
        throw new AndroidRuntimeException(
                "Calling startActivity() from outside of an Activity "
                + " context requires the FLAG_ACTIVITY_NEW_TASK flag."
                + " Is this really what you want?");
    }
    mMainThread.getInstrumentation().execStartActivity(
        getOuterContext(), mMainThread.getApplicationThread(), null,
        (Activity)null, intent, -1, options);
}
```
- 通过跟进代码可以发现，不管哪种方式启动Activity，最终都是通过Instrumentation.execStartActivity来启动Activity的。

``` java
public ActivityResult execStartActivity(
           Context who, IBinder contextThread, IBinder token, Activity target,
           Intent intent, int requestCode, Bundle options) {
       IApplicationThread whoThread = (IApplicationThread) contextThread;
       if (mActivityMonitors != null) {
           synchronized (mSync) {
               final int N = mActivityMonitors.size();
               for (int i=0; i<N; i++) {
                   final ActivityMonitor am = mActivityMonitors.get(i);
                   if (am.match(who, null, intent)) {
                       am.mHits++;
                       if (am.isBlocking()) {
                           return requestCode >= 0 ? am.getResult() : null;
                       }
                       break;
                   }
               }
           }
       }
       try {
           intent.migrateExtraStreamToClipData();
           intent.prepareToLeaveProcess();
           int result = ActivityManagerNative.getDefault()
               .startActivity(whoThread, who.getBasePackageName(), intent,
                       intent.resolveTypeIfNeeded(who.getContentResolver()),
                       token, target != null ? target.mEmbeddedID : null,
                       requestCode, 0, null, options);
           checkStartActivityResult(result, intent);
       } catch (RemoteException e) {
       }
       return null;
   }
```

- 而mInstrumentation最终又调用到了ActivityManagerNative.getDefault()中。

``` files
static public IActivityManager getDefault() {
    return gDefault.get();
}

private static final Singleton<IActivityManager> gDefault = new Singleton<IActivityManager>() {
    protected IActivityManager create() {
        IBinder b = ServiceManager.getService("activity
        IActivityManager am = asInterface(
        return am;
    }
};
```
- 由于整个Framework与AMS打交道是如此频繁，framework使用了一个单例把这个AMS的代理对象保存了起来；这样只要需要与AMS进行IPC调用，获取这个单例即可。这是AMS这个系统服务与其他普通服务的不同之处。这里还有一点小麻烦：Android不同版本之间对于如何保存这个单例的代理对象是不同的；Android 2.x系统直接使用了一个简单的静态变量存储，Android 4.x以上抽象出了一个Singleton类

### PM 获取过程
- 和ActivityManager比较类似，我直接上代码：

``` java
@Override
 public PackageManager getPackageManager() {
     if (mPackageManager != null) {
         return mPackageManager;
     }

     IPackageManager pm = ActivityThread.getPackageManager();
     if (pm != null) {
         // Doesn't matter if we make more than one instance.
         return (mPackageManager = new ApplicationPackageManager(this, pm));
     }

     return null;
 }

 public static IPackageManager getPackageManager() {
     if (sPackageManager != null) {
         //Slog.v("PackageManager", "returning cur default = " + sPackageManager);
         return sPackageManager;
     }
     IBinder b = ServiceManager.getService("package");
     //Slog.v("PackageManager", "default service binder = " + b);
     sPackageManager = IPackageManager.Stub.asInterface(b);
     //Slog.v("PackageManager", "default service = " + sPackageManager);
     return sPackageManager;
 }

```
- 通过上面的代码我们可以看出，我们需要hook sPackageManager为我们的代理对象。

## Activity生命周期管理
- 在Android系统上，仅仅完成动态类加载是不够的；我们需要想办法把我们加载进来的Activity等组件交给系统管理，让AMS赋予组件生命周期。

### Activity启动过程
- Activity启动过程已经看了无数次，流程比较长，这里只是对大致的过程进行说明，下图是App进程和AMS进程的通信过程：
![Activity_app_AMS.png](/upload/image/zlw/Activity_app_AMS.png)
- App进程会委托AMS进程完成Activity生命周期的管理及任务栈的管理，这个通信过程AMS是Server端，APP进程通过持有AMS的client代理ActivityManagerNative完成通信过程。
- AMS进程完成生命周期管理以及任务栈管理后，会把控制权交给App进程，让App进程完成Activity类对象的创建，以及生命周期回调；这个通信过程也是通过Binder完成的，App所在server端的Binder对象存在于ActivityThread的内部类ApplicationThread；AMS所在client通过持有IApplicationThread的代理对象完成对于App进程的通信。
- App进程内部的ApplicationThread server端内部有自己的Binder线程池，它与App主线程的通信通过Handler完成，这个Handler存在于ActivityThread类，叫做H。
- 最终会调用到ActivityThread类中的performLaunchActivity方法，这个方法做了两件重要的事情：

>1.使用ClassLoader加载并通过反射创建Activity对象。
>2. 如果Application还没有创建，那么创建Application对象并回调相应的生命周期方法；

### 启动不在AndroidManifest.xml中声明的Activity。
- 根据上面Activity启动过程分析，(假如我们想启动TargetActivity，但是我们在AndroidManifest中配置了替换Activity，假如是SubActivity)我们的实现方案是这样：
>1. 我们启动Activity，还是用的new Intent（context，TargetActivity.class）.startActivity()。然后我们利用到之前hook的ActivityManager,即在Activity即将要交给AMS去处理时，替换成为SubActivity，因为TargetActivity未在AndroidManifest中配置，而AMS有权限校验，会报错，等于我们交给AMS的是一个假的中转Activity。
>2. AMS处理之后，会交给ApplicationThread处理，最终会调用到ActivityThread中内部类H，通过H来切换到主线程，这个H就是一个Handler，接下来就会在ActivityThread中执行对应的方法，所以在这里我们hook Handler的mCallback，将SubActivity再替换成我们的TargetActivity，这样完成启动。
>3. 对于AMS来说，它只知道SubActivity的存在，因此我们在TargetActivity页面取dumpActivity栈，得到的还是SubActivity。


### 启动的Activity能否收到正确声明周期
- 其实从上面的学习我们可以明确一点，App进程和AMS交互模式比较固定：几个角色：ActivityManagerNative、ApplicationThread、ActivityThread以及内部类H，调用者使用Activity的方法，会通过ActivityManagerNative执行AMS的方法，然后再通过ApplicationThread执行到ActivityThread，然后交给内部类H转发消息到ActivityThread的方法。
- 在Activity内有一个成员变量mToken，token可以唯一标识一个Activity对象，在AMS中，它只知道在AndroidManifest.xml中声明的Activity的存在，我们可以通过dump activity栈发现，当前页面还是在AndroidManifest中声明的Activity。
- 然而在启动Activity的时候，回调到performLaunActivity时，通过ClassLoader加载了我们真正的Activity，完成这一操作后将activity添加进mActivities中，另外在这个方法中，我们还能看到对Activity attach方法的调用，它传递了新创建的Activity一个token对象，而这个token是在ActivityClientRecord构造函数中初始化的。
- 因此通过这种方式启动的Activity有它自己完整而独立的生命周期！
