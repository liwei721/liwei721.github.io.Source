---
title: java资源关闭相关
date: 2017-06-13 13:51:39
tags: java资源关闭, 静态代码扫描
categories: java知识
---
## 背景
- 最近在看静态代码扫描的一些文章，主要是参考360火线团队写的一些文章，其中他们对资源关闭做了深入研究，所以我这里也做个记录。
- 资源不关闭，久而久之，类似内存会被大量占用，造成内存溢出，并且会造成某些硬件资源的浪费，因此我们必须扼杀在编码阶段，静态代码扫描是非常有必要的。

## 为什么要手动关闭java资源对象？
- 首先解释Java的资源对象，它主要包括IO对象，数据库连接对象。比如常见的InputStream、OutputStream、Reader、Writer、Connection、Statement、ResultSet、Socket等等，先代码列举一个例子
``` file
FileInputStream f = new FileInputStream("sample.txt");
f.close();//f对象即需要手动关闭的资源对象
```
- 大家肯定会有疑问，java中有个利器GC，那么为啥还要手动关闭资源呢？ 从常理上讲，java既然提供了close的api，意为关闭资源，那么假如我们不调用，意味着资源没被关闭，也就是还在使用，所以系统是不会回收这部分资源的。
- 从原理上来说，各种stream之类，他们下边一般还开启了各种其他的系统资源，比如文件，比如输入输出设备（键盘/屏幕等），等等。而这些设备第一是不能自动关闭（因为谁知道你程序要用它到什么时候啊），另一个系统内数量有限（比如键盘/屏幕同一时间只有一个）。最后，文件和数据库连接之类的东西还存在读写锁定的问题。这些都导致用户必须手动处理这些资源的开启和关闭。
- 其次为了“避免”程序员忘了自己释放那些资源，Java提供了finalizer、PhantomReference之类的机制来让程序员向GC注册“自动回调释放资源”的功能。但GC回调它们的时机不确定，所以只应该作为最后手段来使用，主要手段还是自己关闭最好。

## 如何正确的关闭资源
- 这里只提三种关闭方法：try-catch-finally 、 try-with-resources、第三方库IOUtils

### try-catch-finally
- 这是我们最常见的写法，如下：

``` file
FileInputStream f;
try{
    f= new FileInputStream("sample.txt");
    //something that uses f and sometimes throws an exception
}
catch(IOException ex){
    /* Handle it somehow */
}
finally{
    f.close();
}
```

### try-with-resources
- 这是从java1.7开始，官方建议的写法：

``` file
try (
        FileOutputStream fileOutputStream = new FileOutputStream("E:\\A.txt");
        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
        DataOutputStream out = new DataOutputStream(bufferedOutputStream)
        )
{       
    out.write(data1);
} catch (Exception e) {
    // TODO: handle exception
}
```
- 这种写法我之前没见过，这种将流放到try括号内，系统会自动close资源。

### 第三方库IOUtils
- IOUtils.closeQuietly(e)，其本质上也是调用了close方法。

``` java
public static void closeQuietly(final Closeable closeable) {
       try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (final IOException ioe) {
            // ignore
        }
    }
```

## 资源关闭的特殊场景

### ByteArrayInputStream等不需要检查关闭的资源对象
- 有一些资源对象时不用关闭的。这些对象包括：ByteArrayInputStream、ByteArrayOutputStream、StringBufferInputStream、CharArrayWriter、和StringWriter。
- 看官方文档里面对象类对应的close()方法的解释：

``` file
Closing a ByteArrayInputStream has no effect. The methods in this class can be called after the stream has been closed without generating an IOException.
```
- 即调用close没啥影响，即使调用了close方法，再使用流也不会抛出IOException。

### 资源对象在套接使用时，只需要手动关闭最后套接的对象
- 啥叫套结使用呢，我们知道，java Instream 采用了包装模式。看一段代码：

``` file
FileOutputStream fileOutputStream = new FileOutputStream("A.txt");
BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
DataOutputStream out = new DataOutputStream(bufferedOutputStream);
```
- 如上所示，我们只需要调用out.close，就能将所有资源关闭。 被套结的另外两个资源为啥会被关闭呢？重点在于close()方法的实现，out在关闭时实际是先调用了java.io.FilterOutputStream.close()方法，该方法的具体实现如下：

``` java
/**
 * Closes this output stream and releases any system resources
 * associated with the stream.
 * <p>
 * The <code>close</code> method of <code>FilterOutputStream</code>
 * calls its <code>flush</code> method, and then calls the
 * <code>close</code> method of its underlying output stream.
 *
 * @exception  IOException  if an I/O error occurs.
 * @see        java.io.FilterOutputStream#flush()
 * @see        java.io.FilterOutputStream#out
 */
 public void close() throws IOException {
     try {
       flush();
     } catch (IOException ignored) {
     }
     out.close();
 }
```
- 这段代码意思是先调用了flush()方法，保证之前写入到内存的数据刷到硬盘。接着调用java.io.OutputStream.close()方法，继续看java.io.OutputStream.close()的实现：

``` java
/**
 * Closes this output stream and releases any system resources
 * associated with this stream. The general contract of <code>close</code>
 * is that it closes the output stream. A closed stream cannot perform
 * output operations and cannot be reopened.
 * <p>
 * The <code>close</code> method of <code>OutputStream</code> does nothing.
 *
 * @exception  IOException  if an I/O error occurs.
 */
public void close() throws IOException {
}
```
- 注释中Closes this output stream and releases any system resources associated with this stream这句非常重要，意思是关闭这个输出流并释放任何与之相关的系统资源。
- 大家还可以看到方法里面什么都没有做，但是java.io.OutputStream 实现了 Closeable接口，接着Closeable接口集成了AutoCloseable接口，最后定位到AutoCloseable接口中，注释里有这样一句Closes this resource, relinquishing any underlying resources.，大意为关闭这个资源，放弃任何底层的资源

### 数据库连接对象中当Statement被关闭后，由该Statement初始化的ResultSet对象也会自动关闭

``` file
Statement stmt = con.createStatement( ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_UPDATABLE);
ResultSet rs = stmt.executeQuery("SELECT a, b FROM TABLE2");
```
- 从代码可以看出Statement和ResultSet的关系，其中当Statement对象关闭之后，由Statement对象初始化的ResultSet对象rs也会被自动关闭。

``` file
When a Statement object is closed, its current ResultSet object, if one exists, is also closed.
```

### 使用socket获取的InputStream和OutputStream对象不需要关闭
- 使用socket创建出的InputStream和OutputStream，当socket关闭时，这两个流也会自动关闭。同时，如果关闭InputStream，将会同时关闭与之相关的Socket。

``` file
Socket socket = new Socket("127.0.0.1", 8001);
InputStream input = socket.getInputStream();
OutputStream output = socket.getOutputStream();
```

- 我们查看下官方文档：

``` file
Closing this socket will also close the socket's InputStream and OutputStream.
If this socket has an associated channel then the channel is closed as well.
Closing the returned InputStream will close the associated socket.
```

## 总结
- 资源关闭在做静态代码扫描时是非常常见的问题，因此对某些常见问题的了解还是很有必要的。
