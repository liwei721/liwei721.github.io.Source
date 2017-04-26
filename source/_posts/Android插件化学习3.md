---
title: Android插件化学习记录（三）
date: 2017-04-18 22:33
tags: Android, 插件化
categories: Android技术
---

## Service工作原理
- service有两种形式：以startService启动的服务和用bindService绑定的服务。我们来分析bindService方法，它最终会调用到ContextImpl中的bindServiceCommon方法：

``` java
/**
  第一个参数是想要绑定的Service的Intent
  第二个参数是通过这个对象接收到Service绑定成功或失败的回调
  第三个参数是绑定时的一些FLAG
*/
private boolean bindServiceCommon(Intent service, ServiceConnection conn, int flags,
        UserHandle user) {
    //    IServiceConnection与IApplicationThread以及IIntentReceiver相同，都是ActivityThread给AMS提供的用来与之进行通信的Binder对象；这个接口的实现类///、、为LoadedApk.ServiceDispatcher。
    IServiceConnection sd;
    if (conn == null) {
        throw new IllegalArgumentException("connection is null");
    }
    if (mPackageInfo != null) {
        // important
        sd = mPackageInfo.getServiceDispatcher(conn, getOuterContext(),
                mMainThread.getHandler(), flags);
    } else {
        throw new RuntimeException("Not supported in system context");
    }
    validateServiceIntent(service);
    try {
        IBinder token = getActivityToken();
        if (token == null && (flags&BIND_AUTO_CREATE) == 0 && mPackageInfo != null
                && mPackageInfo.getApplicationInfo().targetSdkVersion
                < android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            flags |= BIND_WAIVE_PRIORITY;
        }
        service.prepareToLeaveProcess();
        int res = ActivityManagerNative.getDefault().bindService(
            mMainThread.getApplicationThread(), getActivityToken(), service,
            service.resolveTypeIfNeeded(getContentResolver()),
            sd, flags, getOpPackageName(), user.getIdentifier());
        if (res < 0) {
            throw new SecurityException(
                    "Not allowed to bind to service " + service);
        }
        return res != 0;
    } catch (RemoteException e) {
        throw new RuntimeException("Failure from system", e);
    }
}
```
- 接下来就是AMS中bindService方法，它直接调用了ActiveServices的bindServiceLocked方法，代码就不贴了，它主要做了下面两件事情：
>1. 通过retrieveServiceLocked方法获取到intent匹配到的需要bind到的service组件res。
>2. 把ActivityThread传递过来的IserviceCOnnection使用ConnectionRecord进行包装。方便接下来使用。
>3. 启动的FLAG为BIND_AUTO_CREATE,(默认就是这种启动方式)，那么调用bringUpServiceLocked开始创建Service。

- 接下来跟踪bringupServiceLocked方法，看如何创建Service。这个方法主要做了两件事情：
>1. 如果Service所在的进程已经启动，直接调用realStartServiceLocked方法来真正启动Service组件。
>2. 如果Service所在的进程没有启动，那么现在AMS中记下要启动的Service组件，然后通过startProcessLocked启动新的进程。

- 接下来我们跟踪realStartServiceLocked，这里也不上代码了，只写它主要做了什么，方便理解：
>1. 调用了app.thread的scheduleCreateService方法，这是一个IApplicationThread对象,用于app进程和AMS之间的通信。

- 因此接下来应该跟踪到ActivityThread类中scheduleCreateService方法，这个方法也比较简单，它向内部类H发送了消息：CREATE_SERVICE。这种模式我们并不陌生，Activity的启动就有这一步。接下来看H中的handleCreateService方法：

``` java
private void handleCreateService(CreateServiceData data) {
    unscheduleGcIdler();

    LoadedApk packageInfo = getPackageInfoNoCheck(
            data.info.applicationInfo, data.compatInfo);
    Service service = null;
    try {
        java.lang.ClassLoader cl = packageInfo.getClassLoader();
        service = (Service) cl.loadClass(data.info.name).newInstance();
    } catch (Exception e) {
    }

    try {
        ContextImpl context = ContextImpl.createAppContext(this, packageInfo);
        context.setOuterContext(service);

        Application app = packageInfo.makeApplication(false, mInstrumentation);
        service.attach(context, this, data.info.name, data.token, app,
                ActivityManagerNative.getDefault());
        service.onCreate();
        mServices.put(data.token, service);
        try {
            ActivityManagerNative.getDefault().serviceDoneExecuting(
                    data.token, SERVICE_DONE_EXECUTING_ANON, 0, 0);
        } catch (RemoteException e) {
            // nothing to do.
        }
    } catch (Exception e) {
    }
}
```
- 这个方法里就有我们非常熟悉的内容了。
>1. 首先获取一个LoadedApk。
>2. 通过反射来创建一个Service对象。并且调用了它的onCreate对象。
>3. scheduleCreateService这个Binder调用过程结束，代码又回到了AMS进程的realStartServiceLocked方法,这个方法中接着执行requestServiceBindingsLocked方法。

- requestServiceBindingsLocked这个方法是处理bindService的过程。它又调用了r.app.thread.scheduleBindService（即又通过IApplicationThread binder调用到ActivityThread，再转发到H 中（Handler）），真正的处理在handleBindService中。
- handleBindService方法里面最主要的是通过AMS进行publishService。我们继续跟踪到AMS的publishService。它其实是调用了publishServiceLocked方法。

``` java
void publishServiceLocked(ServiceRecord r, Intent intent, IBinder service) {
    final long origId = Binder.clearCallingIdentity();
    try {
        if (r != null) {
            Intent.FilterComparison filter
                    = new Intent.FilterComparison(intent);
            IntentBindRecord b = r.bindings.get(filter);
            if (b != null && !b.received) {
                b.binder = service;
                b.requested = true;
                b.received = true;
                for (int conni=r.connections.size()-1; conni>=0; conni--) {
                    ArrayList<ConnectionRecord> clist = r.connections.valueAt(conni);
                    for (int i=0; i<clist.size(); i++) {
                        ConnectionRecord c = clist.get(i);
                        if (!filter.equals(c.binding.intent.intent)) {
                            continue;
                        }
                        try {
                            c.conn.connected(r.name, service);
                        } catch (Exception e) {
                        }
                    }
                }
            }

            serviceDoneExecutingLocked(r, mDestroyingServices.contains(r), false);
        }
    } finally {
        Binder.restoreCallingIdentity(origId);
    }
}
```

- 这个方法主要是取出来前面保存的封装在ServiceRecord中的IServiceConnection对象，然后调用它的connected方法。
- 这个IServiceConnection是一个Binder对象，它的实现类在LoadedApk.ServiceDispatcher里面。最终是ServiceConnection的回调过程。
- 上面我们介绍的是进程已经存在的情况，如果Service所在进程不存在，那么会调用startProcessLocked方法创建一个新的进程，并把需要启动的Service放在一个队列里面；创建进程的过程通过Zygote fork出来，进程创建成功之后会调用ActivityThread的main方法，在这个main方法里面间接调用到了AMS的attachApplication方法，在AMS的attachApplication里面会检查刚刚那个待启动Service队列里面的内容，并执行Service的启动操作；之后的启动过程与进程已经存在的情况下相同。

## service组件的插件化

### Service与Activity的异同
#### 用于交互对生命周期的影响
- Activity的声明周期受用户交互影响，这种变化只有Android系统才能感知，因此必须把插件的Activity交给系统管理，才能拥有完整的生命周期，但是Service组件的生命周期不受外界因素影响，那么我们可以手动控制它的生命周期。

#### Activity的任务栈
- Activity有栈的概念，Activity栈不能太深（不然用户需要多次点back体验不好）。因此我们在实现Activity插件化时，只使用有限个StubActivity，就能满足无数插件Activity的需求。
- 但是Service没有这个概念，理论上可以启动Service组件是无限的。除了硬件以及内存资源，没有什么限制它的数目，所以我们在AndroidManifest中写无限多的SubService不太现实。
- Service的生命周期如下：
![service_lifecycle.png](/upload/image/zlw/service_lifecycle.png)

- 如果以startService方式启动插件Service，直接回调要启动的Service对象的onStartCommand方法即可；如果用stopService或者stopSelf的方式停止Service，只需要回调对应的Service组件的onDestroy方法。
- 如果用bindService方式绑定插件Service，可以调用对应Service对应的onBind方法，获取onBind方法返回的Binder对象，然后通过ServiceConnection对象进行回调统计；unBindService的实现同理。

### 代理分发技术
- 我们希望插件的Service具有一定的运行时优先级，那么一个货真价实的Service组件是必不可少的——只有这种被系统认可的真正的Service组件才具有所谓的运行时优先级。
- 因此，我们可以注册一个真正的Service组件ProxyService，让这个Service承载一个真正的Service组件所具备的能力（进程优先级等）；当启动插件的服务比如PluginService的时候，我们统一启动这个ProxyService，当这个ProxyService运行起来之后，再在它的onStartCommand等方法里面进行分发，执行PluginService的onStartCommond等对应的方法；我们把这种方案形象地称为「代理分发技术」
- 代理分发技术也可以完美解决插件Service可以运行在不同的进程的问题——我们可以在AndroidManifest.xml中注册多个ProxyService，指定它们的process属性，让它们运行在不同的进程；当启动的插件Service希望运行在一个新的进程时，我们可以选择某一个合适的ProxyService进行分发。也许有童鞋会说，那得注册多少个ProxyService才能满足需求啊？理论上确实存在这问题，但事实上，一个App使用超过10个进程的几乎没有；因此这种方案是可行的。
































+++
