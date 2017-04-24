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










































++++
