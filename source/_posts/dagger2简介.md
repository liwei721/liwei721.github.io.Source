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








































++++++++
