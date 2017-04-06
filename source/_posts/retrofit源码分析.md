---
title: Retrofit简单实用及源码分析
date: 2016-12-3 14:49:23
tags: retrofit
categories: Android开源库
---

## 背景
- Retrofit是Square公司开发的一款针对Android网络请求的框架，Retrofit2底层基于OkHttp实现的。
- 官方对它的解释是：A type-safe HTTP client for Android and Java ，就是一个安全的可用于Android和java的网络库。
- 它可以简化我们操作网络的成本，其实所有第三方库的功能，我们用原生的api都是可以实现的，只不过需要花费时间和精力，且在某些场景下可能出现bug和性能不太好，而第三方开源库已经有很多产品使用，得到了验证和完善，所以我们推荐使用第三方库，不过我们需要对开源库的实现原理了解，这样在出现问题的时候能方便的排查问题。

### Rest API
- 这里简单说下什么是Rest API。 REST是一种架构风格，用于创建Web服务，几乎总是工作在HTTP上。它的工作原理几乎以同样的方式得到的网页，也有标准的方法来创建，更新和删除。

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

``` java
public Builder() {
  this(Platform.get());
}

/**接下来看Platform主要做了什么，其实就是findPlatform,用来查找当前的平台是啥：android？java？ios？
*/

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

/**接下来是Android类， 它继承了Platform重写了defaultCallbackExecutor和defaultCallAdapterFactory方法。
//defaultCallbackExecutor：返回的是用于执行 Callback 的 线程池。可以看到MainThreadExecutor 获取了主线程的 Looper 并构造了一个主线程的 Handler，调用 Callback 时会将该请求 post 到主线程上去执行。这就解释了为什么请求后完成的回调都是在主线中。
// defaultCallAdapterFactory：将返回的适配类型默认为Call类型（如果使用RxJava的话，就可以通过配置.addCallAdapterFactory(RxJavaCallAdapterFactory.create())将配置类型改成Observable。）
*/

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
- 再看addConverterFactory(GsonConverterFactory.create())：
``` java
// 往转换工厂集合中添加了我们指定的转换工厂.在我们的例子里面 GsonConverterFactory 将选用 GsonConverter 来转换。
public Builder addConverterFactory(Converter.Factory factory) {
  converterFactories.add(checkNotNull(factory, "factory == null"));
  return this;
}
```
- 接下来是build的过程：
``` java
public Retrofit build() {
  if (baseUrl == null) {
    throw new IllegalStateException("Base URL required.");
  }

  okhttp3.Call.Factory callFactory = this.callFactory;
  if (callFactory == null) {
    callFactory = new OkHttpClient();
  }

  Executor callbackExecutor = this.callbackExecutor;
  if (callbackExecutor == null) {
    callbackExecutor = platform.defaultCallbackExecutor();
  }

  // Make a defensive copy of the adapters and add the default Call adapter.
  List<CallAdapter.Factory> adapterFactories = new ArrayList<>(this.adapterFactories);
  adapterFactories.add(platform.defaultCallAdapterFactory(callbackExecutor));

  // Make a defensive copy of the converters.
  List<Converter.Factory> converterFactories = new ArrayList<>(this.converterFactories);
  return new Retrofit(callFactory, baseUrl, converterFactories, adapterFactories,
      callbackExecutor, validateEagerly);
}
// 这就是创建Retorfit对象的地方，将前面几步配置的参数都设置到Retrofit中，这里可以很明确的看到默认callFactory用的是OKHttpClient
```
### 创建接口实例并调用
- 对应上面的代码：
``` java
APIService service = retrofit.create(APIService.class);
Call<NewsInfo> call = service.getNews("10");
```
- 我们先看一下create的代码,它主要做了什么我都写到注释中：
``` java
public <T> T create(final Class<T> service) {
    // 检查service是否是个接口
    Utils.validateServiceInterface(service);
    // 检查是否要提前创建serviceMethod
    if (validateEagerly) {
      eagerlyValidateMethods(service);
    }

    // 这里就是设计非常巧妙的地方了，用到了java动态代理，我会在另外一篇文章中讲动态代理
    // 这里返回的是一个动态代理，然后又转成了对应的接口类型
    return (T) Proxy.newProxyInstance(service.getClassLoader(), new Class<?>[] { service },
        new InvocationHandler() {
          private final Platform platform = Platform.get();

          @Override public Object invoke(Object proxy, Method method, Object[] args)
              throws Throwable {
            // If the method is a method from Object then defer to normal invocation.
            // 判断是会否为object方法，是的话就直接调用
            if (method.getDeclaringClass() == Object.class) {
              return method.invoke(this, args);
            }

            // 这里现在默认是直接返回false的。
            if (platform.isDefaultMethod(method)) {
              return platform.invokeDefaultMethod(method, service, proxy, args);
            }

            // 接下来主要做了以下事情：
            // 1. 去serviceMethodCache，查是否有对应serviceMethod的缓存。执行完一遍会缓存下来。
            // 2. 如果不存在缓存就去创建一个，所以下面我们重点分析创建build的过程。
            //
            ServiceMethod<Object, Object> serviceMethod =
                (ServiceMethod<Object, Object>) loadServiceMethod(method);
            OkHttpCall<Object> okHttpCall = new OkHttpCall<>(serviceMethod, args);
            return serviceMethod.callAdapter.adapt(okHttpCall);
          }
        });
  }
```

- 接下来我们看下ServiceMethod的build过程：

``` java
public ServiceMethod build() {
      // callAdapter 是从CallAdapterFactory中选择合适的callAdapter
      callAdapter = createCallAdapter();
      // 获取返回类型
      responseType = callAdapter.responseType();
      if (responseType == Response.class || responseType == okhttp3.Response.class) {
        throw methodError("'"
            + Utils.getRawType(responseType).getName()
            + "' is not a valid response body type. Did you mean ResponseBody?");
      }

      // 去获取一个合适的responseConverter，我们例子中用到的是GsonConverter
      responseConverter = createResponseConverter();

      // 解析各种注解。比如GET POST、PATCH、DELETE等等
      for (Annotation annotation : methodAnnotations) {
        parseMethodAnnotation(annotation);
      }

      if (httpMethod == null) {
        throw methodError("HTTP method annotation is required (e.g., @GET, @POST, etc.).");
      }

      if (!hasBody) {
        if (isMultipart) {
          throw methodError(
              "Multipart can only be specified on HTTP methods with request body (e.g., @POST).");
        }
        if (isFormEncoded) {
          throw methodError("FormUrlEncoded can only be specified on HTTP methods with "
              + "request body (e.g., @POST).");
        }
      }

      // parameterAnnotationsArray是method.getParameterAnnotations() 获取的是方法的参数和参数的注解
      // 比如本例中@Query("limit") String limit
      int parameterCount = parameterAnnotationsArray.length;
      parameterHandlers = new ParameterHandler<?>[parameterCount];
      for (int p = 0; p < parameterCount; p++) {
        Type parameterType = parameterTypes[p];
        if (Utils.hasUnresolvableType(parameterType)) {
          throw parameterError(p, "Parameter type must not include a type variable or wildcard: %s",
              parameterType);
        }

        Annotation[] parameterAnnotations = parameterAnnotationsArray[p];
        if (parameterAnnotations == null) {
          throw parameterError(p, "No Retrofit annotation found.");
        }
        // 将处理过后的parameterHandler放到parameterHandlers中
        parameterHandlers[p] = parseParameter(p, parameterType, parameterAnnotations);
      }

      if (relativeUrl == null && !gotUrl) {
        throw methodError("Missing either @%s URL or @Url parameter.", httpMethod);
      }
      if (!isFormEncoded && !isMultipart && !hasBody && gotBody) {
        throw methodError("Non-body HTTP method cannot contain @Body.");
      }
      if (isFormEncoded && !gotField) {
        throw methodError("Form-encoded method must contain at least one @Field.");
      }
      if (isMultipart && !gotPart) {
        throw methodError("Multipart method must contain at least one @Part.");
      }
      // 创建一个ServiceMethod对象，里面包含了所有的网络请求及返回结果处理相关的数据：
      /**
    this.callFactory = builder.retrofit.callFactory();
    this.callAdapter = builder.callAdapter;
    this.baseUrl = builder.retrofit.baseUrl();
    this.responseConverter = builder.responseConverter;
    this.httpMethod = builder.httpMethod;
    this.relativeUrl = builder.relativeUrl;
    this.headers = builder.headers;
    this.contentType = builder.contentType;
    this.hasBody = builder.hasBody;
    this.isFormEncoded = builder.isFormEncoded;
    this.isMultipart = builder.isMultipart;
    this.parameterHandlers = builder.parameterHandlers;
    */
      return new ServiceMethod<>(this);
    }

    // 由上可知，ServiceMethod其实就是一个控制器一样的东西，处理网络请求。
```
- 接下来就是创建一个OKHttpCall

``` java
OkHttpCall<Object> okHttpCall = new OkHttpCall<>(serviceMethod, args);
// 我们看下OKHttpCall是个什么鬼东西
final class OkHttpCall<T> implements Call<T> {
  private final ServiceMethod<T, ?> serviceMethod;
  private final Object[] args;

  private volatile boolean canceled;

  // All guarded by this.
  // 我们看到底层用到了okhttp3
  private okhttp3.Call rawCall;
  private Throwable creationFailure; // Either a RuntimeException or IOException.
  private boolean executed;

  OkHttpCall(ServiceMethod<T, ?> serviceMethod, Object[] args) {
    this.serviceMethod = serviceMethod;
    this.args = args;
  }
  @Override public synchronized Request request() {}
  @Override public void enqueue(final Callback<T> callback) {}
}
```
- 接下来就是返回处理

``` java
serviceMethod.callAdapter.adapt(okHttpCall);

// 这里的逻辑，要跟踪到创建retrofit对象的代码中
// Make a defensive copy of the adapters and add the default Call adapter.  Retrofit.java
Executor callbackExecutor = this.callbackExecutor;
if (callbackExecutor == null) {
  callbackExecutor = platform.defaultCallbackExecutor();
}

// Make a defensive copy of the adapters and add the default Call adapter.
List<CallAdapter.Factory> adapterFactories = new ArrayList<>(this.adapterFactories);
adapterFactories.add(platform.defaultCallAdapterFactory(callbackExecutor));
// 继续跟踪代码，最后会进入到ExecutorCallAdapterFactory中，这是库默认的一个CallAdapter。
final class ExecutorCallAdapterFactory extends CallAdapter.Factory {
  final Executor callbackExecutor;

  ExecutorCallAdapterFactory(Executor callbackExecutor) {
    this.callbackExecutor = callbackExecutor;
  }

  @Override
  public CallAdapter<?, ?> get(Type returnType, Annotation[] annotations, Retrofit retrofit) {
    if (getRawType(returnType) != Call.class) {
      return null;
    }
    final Type responseType = Utils.getCallResponseType(returnType);
    return new CallAdapter<Object, Call<?>>() {
      @Override public Type responseType() {
        return responseType;
      }

      // 因此serviceMethod.callAdapter.adapt(okHttpCall)最后返回的是一个ExecutorCallbackCall，这里用到了适配器模式
      @Override public Call<Object> adapt(Call<Object> call) {
        return new ExecutorCallbackCall<>(callbackExecutor, call);
      }
    };
  }

```

### 发起请求
- 上一步提到，当执行定义的service方法，本例中是getNews，会返回一个ExecutorCallbackCall，我们接下来看下这个类的结构：
``` java
static final class ExecutorCallbackCall<T> implements Call<T> {
    final Executor callbackExecutor;
    final Call<T> delegate;

    ExecutorCallbackCall(Executor callbackExecutor, Call<T> delegate) {
      this.callbackExecutor = callbackExecutor;
      this.delegate = delegate;
    }

    @Override public void enqueue(final Callback<T> callback) {
      if (callback == null) throw new NullPointerException("callback == null");

      delegate.enqueue(new Callback<T>() {
        @Override public void onResponse(Call<T> call, final Response<T> response) {
          callbackExecutor.execute(new Runnable() {
            @Override public void run() {
              if (delegate.isCanceled()) {
                // Emulate OkHttp's behavior of throwing/delivering an IOException on cancellation.
                callback.onFailure(ExecutorCallbackCall.this, new IOException("Canceled"));
              } else {
                callback.onResponse(ExecutorCallbackCall.this, response);
              }
            }
          });
        }

        @Override public void onFailure(Call<T> call, final Throwable t) {
          callbackExecutor.execute(new Runnable() {
            @Override public void run() {
              callback.onFailure(ExecutorCallbackCall.this, t);
            }
          });
        }
      });
    }

    @Override public boolean isExecuted() {
      return delegate.isExecuted();
    }

    @Override public Response<T> execute() throws IOException {
      return delegate.execute();
    }

    @Override public void cancel() {
      delegate.cancel();
    }

    @Override public boolean isCanceled() {
      return delegate.isCanceled();
    }

    @SuppressWarnings("CloneDoesntCallSuperClone") // Performing deep clone.
    @Override public Call<T> clone() {
      return new ExecutorCallbackCall<>(callbackExecutor, delegate.clone());
    }

    @Override public Request request() {
      return delegate.request();
    }
  }
```
- 类里面有两个关键方法：enqueue（用于异步执行网络请求）、request（用于同步执行网络请求）。
- enqueue方法，是callbackExecutor.execute(Runnable),这又得联想到刚才构建Android platform时创建了一个defaultCallbackExecutor，将runnable post到主线程。
- 再结合上面写的例子，相信你已经大致了解执行过程了。


### 将返回结果转成我们需要的类型
- 最后补上一个点，怎么转换类型，例子中我们用的是Gson，所以我就结合着代码说一下它是怎么转换的。首先我们在构造Retrofit时传入了一个 GsonConverterFactory.create()。

``` java
/**
  * Create an instance using {@code gson} for conversion. Encoding to JSON and
  * decoding from JSON (when no charset is specified by a header) will use UTF-8.
  */
  //  它最终会创建一个GsonConverterFactory
 public static GsonConverterFactory create(Gson gson) {
   return new GsonConverterFactory(gson);
 }
```
- 在GsonConvertFactory中有两个非常重要的方法：

``` java
/**
*  创建一个返回结果的Converter
*/
@Override
  public Converter<ResponseBody, ?> responseBodyConverter(Type type, Annotation[] annotations,
      Retrofit retrofit) {
    TypeAdapter<?> adapter = gson.getAdapter(TypeToken.get(type));
    return new GsonResponseBodyConverter<>(gson, adapter);
  }

  @Override
  public Converter<?, RequestBody> requestBodyConverter(Type type,
      Annotation[] parameterAnnotations, Annotation[] methodAnnotations, Retrofit retrofit) {
    TypeAdapter<?> adapter = gson.getAdapter(TypeToken.get(type));
    return new GsonRequestBodyConverter<>(gson, adapter);
  }

```
- 上面提到的网络请求，最终会由okhttp执行，最后拿到回调结果后，会执行到ServiceMethod中：

``` java
/** Builds a method return value from an HTTP response body. */
/**
  这里的responseConverter就是上面创建的GsonRequestBodyConverter
*/
 R toResponse(ResponseBody body) throws IOException {
   return responseConverter.convert(body);
 }
```

- 我们来看看GsonRequestBodyConverter的convert方法都做了什么

``` java
/**
  它主要就是调用Gson的接口，将json转成了对应的对象。
  这里的adapter 是这么得来的  TypeAdapter<?> adapter = gson.getAdapter(TypeToken.get(type)); 也就是得到返回类的adapter
*/
@Override public T convert(ResponseBody value) throws IOException {
    JsonReader jsonReader = gson.newJsonReader(value.charStream());
    try {
      return adapter.read(jsonReader);
    } finally {
      value.close();
    }
  }
```
- 最后回调给Callback的onSuccess或者onFailure。
- 中间略去一些过程，我觉得结合代码是比较容易理解的，我这里就列出一个大致的流程思路。

## 总结
- Retrofit的设计非常巧妙，将各个模块都解耦，可以根据自己的需要来组装。因此不得不对大牛的设计感到惊叹。
- 第一次看可能会看的有点迷糊，建议结合源码多次看。相信每次的收获都会不同吧。
