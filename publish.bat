@echo off
title = ������������
echo ׼����������Դ�뵽github��������
git pull 1>nul
git add . 1>nul 
set str=
set /p "str=�������ύ��־��Ϣ��"
if defined str (
    git commit -am %str% 1>nul
) else git commit -am "Ĭ����־��Ϣ�޸��˲���" 1>nul
git push -u origin master 1>nul
echo �Ѿ�������Դ�뷢����github
echo ��ʼ���ɾ�̬html��������github
hexo g 1>nul && hexo d 1>nul  && echo �Ѿ��������£�10s�����https://xdjatesterhome.github.io/�鿴�������
