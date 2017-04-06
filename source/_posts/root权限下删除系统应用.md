---
title: root权限下删除系统应用
date: 2016-11-11 15:37:14
tags: 删除系统应用
categories: 工具使用
---

## 背景
- 测试过程中会碰到有些安装包无法安装的情况，错误一般是：[INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES],代表的意思是这个应用已经是内置应用了且他们签名不一样，所以会安装失败。
- 这种情况比较抓狂，自己的测试包装不上去。我们可以用360手机助手或者豌豆荚，他们都能够管理内置应用，不过前提是需要手机已经root。
- 但是装360太流氓，会不经过你同意装好多恶心的东西，所以我们尝试自己动手来搞吧。

## 步骤
- 首先执行命令
```bash
$ adb remount
这命令的作用是重新挂载文件系统，因为android的文件系统是Read-only的，挂载之后我们就能有读写的权限。
```
- 如果上一步提示错误：remount of system failed: Permission denied,就执行下面的命令
```bash
$ adb root
  restarting adbd as root
  不过执行这个命令需要root权限
```

- 然后可以执行将/system/app/xxx/xxx.apk删除
```bash
$ adb shell rm -r /system/app/xxx
  xxx是你要删除的内置应用apk存放目录。
```

- 这里需要注意下，删除完了之后，再点击app会提示应用未安装或直接报错，需要重新启动手机，应用图标才会被清除（没研究出怎么删除应用图标）。
- 最后记得将/system 变成只读的，为了安全考虑。使用命令：

``` bash
$ adb shell  mount -o ro,remount /system
```

- 接下来就可以安装你自己的apk了。

## 总结
- 删除之前最好备份一下，方便测试之后的恢复。因为删除系统应用可能会造成系统异常。
- 碰到其他问题，可以自己Google下,不过我在ACE上已经实践是可行的。
