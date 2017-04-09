---
title: dagger2简介
date: 2017-04-07 15:53:33
tags: dagger
categories: Android开源库
---

## 背景
- 本文参考[Dagger2 最清晰的使用教程](http://www.jianshu.com/p/24af4c102f62)

### 概念
- dagger2是一个基于JSR-330标准的依赖注入框架，在编译期间自动生成代码，负责依赖对象的创建。
- JSR即Java Specification Requests，意思是java规范提要。而JSR-330则是 Java依赖注入标准。

### 需要了解的原则
#### 依赖倒置原则
- 高层次的模块不应该依赖于低层次的模块，他们都应该依赖于抽象。
- 抽象不应该依赖于具体实现，具体实现应该依赖于抽象。
- 参考[使用Dagger2前你必须了解的一些设计原则](http://www.jianshu.com/p/cc1427e385b5),有个关于下拉刷新的例子。可以很好的说明这个情况。

#### 依赖注入
- 依赖注入是不在类中实例化其他依赖的类，而是先把依赖的类实例化了，然后以参数的方式传入构造函数中。
- 目的是为了让上层模块和依赖进一步解耦。

## Dagger2 API

``` java
public @interface Component {
    Class<?>[] modules() default {};
    Class<?>[] dependencies() default {};
}

public @interface Subcomponent {
    Class<?>[] modules() default {};
}

public @interface Module {
    Class<?>[] includes() default {};
}

public @interface Provides {
}

public @interface MapKey {
    boolean unwrapValue() default true;
}

public interface Lazy<T> {
    T get();
}
```
- 还有在Dagger 2中用到的定义在 JSR-330 （Java中依赖注入的标准）中的其它元素：

``` java
public @interface Inject {
}

public @interface Scope {
}

public @interface Qualifier {
}
```

## @Inject和@Component
- 先来看一段没有使用dagger的依赖注入Demo。MainActivity依赖Pot， Pot依赖Rose

``` java
public class Rose {
    public String whisper()  {
        return "热恋";
    }
}
```
``` java
public class Pot {

    private Rose rose;

    @Inject
    public Pot(Rose rose) {
        this.rose = rose;
    }

    public String show() {
        return rose.whisper();
    }
}
```
``` java
public class MainActivity extends AppCompatActivity {

    private Pot pot;

    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Rose rose = new Rose();
        pot = new Pot(rose);

        String show = pot.show();
        Toast.makeText(MainActivity.this, show, Toast.LENGTH_SHORT).show();
    }
}
```
- 使用Dagger2进行依赖注入如下：

``` java
public class Rose {

    @Inject
    public Rose() {}

    public String whisper()  {
        return "热恋";
    }
}
```
``` java
public class Pot {

    private Rose rose;

    @Inject
    public Pot(Rose rose) {
        this.rose = rose;
    }

    public String show() {
        return rose.whisper();
    }
}
```

``` java
@Component
public interface MainActivityComponent {
    void inject(MainActivity activity);
}
```
``` java
public class MainActivity extends AppCompatActivity {

    @Inject
    Pot pot;

    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 这个类是重新编译后Dagger2自动生成的，所以写这行代码之前要先编译一次
        // Build --> Rebuild Project
        DaggerMainActivityComponent.create().inject(this);
        String show = pot.show();
        Toast.makeText(MainActivity.this, show, Toast.LENGTH_SHORT).show();
    }
}
```
- @Inject用于标记需要注入的依赖，或者标记用于提供依赖的方法。
- @Component可以理解为注入器，在注入依赖的目标类MainActivity使用@component完成注入。

### @Inject
- 依赖注入中第一个并且是最重要的就是@Inject注解。JSR-330标准中的一部分，标记那些应该被依赖注入框架提供的依赖。在Dagger 2中有3种不同的方式来提供依赖：
- 构造器注入，@Inject标注在构造器上有两层意思：
>1. 告诉Dagger2可以使用这个构造器构建对象。如Rose类
>2. 注入构造器所需要的参数的依赖。 如Pot类，构造上的Rose会被注入。
>3. 构造器注入的一个缺点是：如果有多个构造器，我们只能标注其中的一个。

- 属性注入: 如MainActivity类，标注在属性上。被标注的属性不能使用private修饰，否则无法注入。
- 方法注入
>1. 标注在public方法上，Dagger2会在构造器执行之后立即调用这个方法。
>2. 对于依赖需要this对象的时候，使用方法注入可以提供安全的this对象，因为方法注入是在构造器之后执行的。

### @Component
- @Inject注解只是JSR-330中定义的注解，在javax.inject包中。这个注解本身并没有作用，它需要依赖于注入框架才具有意义，用来标记需要被注入框架注入的方法，属性，构造。
- Dagger2是用Component来完成依赖注入的，@Component可以说是Dagger2中最重要的一个注解。
- 如我们上面例子中的@Component定义，它的使用有一些建议：
>1. 使用接口定义，并且@Component注解。
>2. 命名方式为：目标类名+Component，在编译之后会生成DaggerXXXComponent这个类，它是我们定义的接口的实现，在目标类中使用它就可以实现依赖注入。

- Component中一般使用两种方式定义方法：
>1. void inject(目标类  obj);Dagger2会从目标类开始查找@Inject注解，自动生成依赖注入的代码，调用inject可完成依赖的注入。
>2. Object getObj(); 如：Pot getPot();Dagger2会到Pot类中找被@Inject注解标注的构造器，自动生成提供Pot依赖的代码，这种方式一般为其他Component提供依赖。

#### Component和Inject的关系如下：
![dagger_component.png](/upload/image/zlw/dagger_component.png)

- Dagger2框架以Component中定义的方法作为入口，到目标类中寻找JSR-330定义的@Inject标注，生成一系列提供依赖的Factory类和注入依赖的Injector类。
而Component则是联系Factory和Injector，最终完成依赖的注入。

#### 看下源码
- Rose_Factory和Pot_Factory分别对应Rose类和Pot类的构造器上的@Inject注解。而Factory其实是个Provider对象：

``` java
public interface Provider<T> {

    /**
     * Provides a fully-constructed and injected instance of {@code T}.
     *
     * @throws RuntimeException if the injector encounters an error while
     *  providing an instance. For example, if an injectable member on
     *  {@code T} throws an exception, the injector may wrap the exception
     *  and throw it to the caller of {@code get()}. Callers should not try
     *  to handle such exceptions as the behavior may vary across injector
     *  implementations and even different configurations of the same injector.
     */
    T get();
}

public interface Factory<T> extends Provider<T> {}
```

- rose类的@Inject注解
``` java
// 这里用到了枚举
public enum Rose_Factory implements Factory<Rose> {
  INSTANCE;

  @Override
  public Rose get() {
    return new Rose();
  }

  public static Factory<Rose> create() {
    return INSTANCE;
  }
}
```

- Pot对象依赖Rose，所以直接将RoseProvide作为参数传入了。

``` java
public final class Pot_Factory implements Factory<Pot> {
  private final Provider<Rose> roseProvider;

  public Pot_Factory(Provider<Rose> roseProvider) {
    assert roseProvider != null;
    this.roseProvider = roseProvider;
  }

  @Override
  public Pot get() {
    return new Pot(roseProvider.get());
  }

  public static Factory<Pot> create(Provider<Rose> roseProvider) {
    return new Pot_Factory(roseProvider);
  }
}
```

- MainActivity上的@Inject属性或方法注解，则对应MainActivity_MembersInjector类

``` java
public interface MembersInjector<T> {

  /**
   * Injects dependencies into the fields and methods of {@code instance}. Ignores the presence or
   * absence of an injectable constructor.
   *
   * <p>Whenever the object graph creates an instance, it performs this injection automatically
   * (after first performing constructor injection), so if you're able to let the object graph
   * create all your objects for you, you'll never need to use this method.
   *
   * @param instance into which members are to be injected
   * @throws NullPointerException if {@code instance} is {@code null}
   */
  void injectMembers(T instance);
}

public final class MainActivity_MembersInjector implements MembersInjector<MainActivity> {
  private final Provider<Pot> potProvider;

  public MainActivity_MembersInjector(Provider<Pot> potProvider) {
    assert potProvider != null;
    this.potProvider = potProvider;
  }

  public static MembersInjector<MainActivity> create(Provider<Pot> potProvider) {
    return new MainActivity_MembersInjector(potProvider);
  }

  @Override
  public void injectMembers(MainActivity instance) {
    if (instance == null) {
      throw new NullPointerException("Cannot inject members into a null reference");
    }
    instance.pot = potProvider.get();
  }

  public static void injectPot(MainActivity instance, Provider<Pot> potProvider) {
    instance.pot = potProvider.get();
  }
}
```

- 最后是DaggerMainActivityComponent类，对应@Component注解就不多说了。这是Dagger2解析JSR-330的入口。它联系Factory和MainActivity两个类完成注入。

``` java
public final class DaggerMainActivityComponent implements MainActivityComponent {
  private Provider<Pot> potProvider;

  private MembersInjector<MainActivity> mainActivityMembersInjector;

  private DaggerMainActivityComponent(Builder builder) {
    assert builder != null;
    initialize(builder);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static MainActivityComponent create() {
    return builder().build();
  }

  @SuppressWarnings("unchecked")
  private void initialize(final Builder builder) {

    this.potProvider = Pot_Factory.create(Rose_Factory.create());

    this.mainActivityMembersInjector = MainActivity_MembersInjector.create(potProvider);
  }

  @Override
  public void inject(MainActivity activity) {
    mainActivityMembersInjector.injectMembers(activity);
  }

  public static final class Builder {
    private Builder() {}

    public MainActivityComponent build() {
      return new DaggerMainActivityComponent(this);
    }
  }
}
```

## @Module和@Provides
- 使用@Inject标记构造提供依赖是有局限性的，比如说我们需要注入的对象时第三方库提供的，我们无法再第三方库的构造器上加上@Inject注解。
- 另外，我们使用依赖倒置时，因为需要注入的对象是抽象的，@Inject也无法使用，因为抽象类并不能实例化。









































++++++++
