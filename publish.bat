@echo off
title = 发布博客文章
echo 准备发布文章源码到github…………
git pull 1>nul
git add . 1>nul 
set str=
set /p "str=请输入提交日志信息："
if defined str (
    git commit -am %str% 1>nul
) else git commit -am "默认日志信息修改了博客" 1>nul
git push -u origin master 1>nul
echo 已经将文章源码发布到github
echo 开始生成静态html并发布到github
hexo g 1>nul && hexo d 1>nul  && echo 已经发布文章，10s后访问https://xdjatesterhome.github.io/查看你的文章
