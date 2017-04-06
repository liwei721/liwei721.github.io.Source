---
title: Retrofit使用简介
date: 2017-3-31 14:49:23
tags: retrofit
categories: Android开源库
---

## 背景
- Retrofit是Square公司开发的一款针对Android网络请求的框架，Retrofit2底层基于OkHttp实现的。
- 官方对它的解释是：A type-safe HTTP client for Android and Java ，就是一个安全的可用于Android和java的网络库。
- 它可以简化我们操作网络的成本，其实所有第三方库的功能，我们用原生的api都是可以实现的，只不过需要花费时间和精力，且在某些场景下可能出现bug和性能不太好，而第三方开源库已经有很多产品使用，得到了验证和完善，所以我们推荐使用第三方库，不过我们需要对开源库的实现原理了解，这样在出现问题的时候能方便的排查问题。

### Rest API
- 这里简单说下什么是Rest API。 REST是一种架构风格，用于创建Web服务，几乎总是工作在HTTP上。它的工作原理几乎以同样的方式得到的网页，但你也有标准的方法来创建，更新和删除。

## 例子
- 首先可以定义一个interface，代码如下所示：
``` java
public interface APIService {
  @GET("News")
  Call<ResponseBody> getNews(
          @Query("limit") String limit);
}
```

- 可以定义一个ServiceManager，里面用来初始化Retrofit，代码如下：
``` java
Retrofit retrofit =new Retrofit.Builder()
              .baseUrl("http://hcy.com/api/")     // 这里baseUrl是所有api的基础url，必须配置
              .addConverterFactory(GsonConverterFactory.create())   //这里是将返回的json转成一个定义好的java对象。
              .build();

```

- 接下来定义一个NewsInfo实体类，实体类的字段是和json对应的，代码如下：

``` java
public class NewsInfo{
  public String title;
  public String detailInfo;
  // 我这里就是举个例子，重点是分析Retrofit源码。
}
```
- 最后就是使用，非常简洁，代码如下：
``` java
APIService service = retrofit.create(APIService.class);
     Call<NewsInfo> call = service.getNews("10");
     call.enqueue(new Callback<NewsInfo>() {
         @Override
         public void onResponse(Call<NewsInfo> call,
                                Response<NewsInfo> response) {
             Log.i(TAG, "onResponse");
         }
         @Override
         public void onFailure(Call<NewsInfo> call, Throwable t) {
             Log.i(TAG, "onFailure");
         }
     });
```
## 代码分析
- Retrofit的一大优点是解耦，其中牵扯到很多设计模式，下面会提到。
### 首先构造Retrofit对象：

``` java
Retrofit retrofit =new Retrofit.Builder()
              .baseUrl("http://hcy.com/api/")     // 这里baseUrl是所有api的基础url，必须配置
              .addConverterFactory(GsonConverterFactory.create())   //这里是将返回的json转成一个定义好的java对象。
              .build();
```
- 这使用到了常见的构造者模式，我们来看builder()做了什么：
```java
public Builder() {
  this(Platform.get());
}

// 接下来看Platform主要做了什么，其实就是findPlatform,用来查找当前的平台是啥：android？java？ios？
private static final Platform PLATFORM = findPlatform();
static Platform get() {
  return PLATFORM;
}
private static Platform findPlatform() {
  try {
      Class.forName("android.os.Build");
      if (Build.VERSION.SDK_INT != 0) {
      return new Android();
    }
    } catch (ClassNotFoundException ignored) {
    }
  try {
      Class.forName("java.util.Optional");
      return new Java8();
    } catch (ClassNotFoundException ignored) {
    }
  try {
      Class.forName("org.robovm.apple.foundation.NSObject");
      return new IOS();
    } catch (ClassNotFoundException ignored) {
    }
  return new Platform();
}

// 接下来是Android类， 它继承了Platform重写了defaultCallbackExecutor和defaultCallAdapterFactory方法。
//defaultCallbackExecutor：返回的是用于执行 Callback 的 线程池。可以看到MainThreadExecutor 获取了主线程的 Looper 并构造了一个主线程的 Handler，调用 Callback 时会将该请求 post 到主线程上去执行。这就解释了为什么请求后完成的回调都是在主线中。
// defaultCallAdapterFactory：将返回的适配类型默认为Call类型（如果使用RxJava的话，就可以通过配置.addCallAdapterFactory(RxJavaCallAdapterFactory.create())将配置类型改成Observable。）
static class Android extends Platform {
  @Override public Executor defaultCallbackExecutor() {
    return new MainThreadExecutor();
  }
  @Override CallAdapter.Factory defaultCallAdapterFactory(Executor callbackExecutor) {
    return new ExecutorCallAdapterFactory(callbackExecutor);
  }

  static class MainThreadExecutor implements Executor {
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override public void execute(Runnable r) {
      handler.post(r);
    }
  }
}

```
- 再看baseUrl()的代码：
``` java
// 通过下面的代码，可以看出其实它拿到baseUrl主要创建了okhttp的HttpUrl。
public Builder baseUrl(String baseUrl) {
checkNotNull(baseUrl, "baseUrl == null");
HttpUrl httpUrl = HttpUrl.parse(baseUrl);
if (httpUrl == null) {
  throw new IllegalArgumentException("Illegal URL: " + baseUrl);
}
return baseUrl(httpUrl);
}
public Builder baseUrl(HttpUrl baseUrl) {
checkNotNull(baseUrl, "baseUrl == null");
List<String> pathSegments = baseUrl.pathSegments();
if (!"".equals(pathSegments.get(pathSegments.size() - 1))) {
  throw new IllegalArgumentException("baseUrl must end in /: " + baseUrl);
}
this.baseUrl = baseUrl;
return this;
}
```
- 在看addConverterFactory(GsonConverterFactory.create())
