---
title: Android问题记录
date: 2017-04-20 12:11:53
tags: Android知识
categories: Android技术
---

## 背景
- 平时工作学习中，有些Android的知识点经常碰到，每次查找原因之后，隔一段时间不碰到，再次碰见还是需要查询。
- 因此就在这里记录下来，方便之后查看。节省时间。

## 知识点

### dex方法数不超过65535
- 在app足够复杂时，在打包时会遇到下面这种错误提示：

``` files
Unable to execute dex: method ID not in [0, 0xffff]: 65536
```

- 为什么方法数目不能超过65K呢，有人说是dexopt的问题，其实dex文件结构是用32位来存储method id 的，并且dexopt是app已经打包成功，安装到手机后才会发生的过程，但是65K问题是在打包时发生的，所以问题不在 dexopt。
- 一般提到的dexopt错误，其实是Android2.3及其以下在dexopt执行时只分配5M内存，导致方法数目过多（数量不一定到65K）时在odex过程中崩溃，官方称之为Dalvik linearAlloc bug。
- 那么65K问题真正的原因是：dalvik bytecode中的指令格式使用了16位来存放@CCCC导致的，不仅Method数据不能超过65K，Field和Class数目也不能超过65K。

### Android中用jxi写excel文件
- 注意追加excel中的sheet时，应该按照下面这种写法，尝试了好多次才实现。

``` java
public static void writeSingleValueToExcel(String sheetName, String[] titles, List<String[]> valuesList,
											   String fileName) throws Exception {
		if (valuesList == null || valuesList.size() < 1){
			return;
		}

		// 创建xls文件
		File file = new File(fileName);
		if (!file.exists()){
			file.createNewFile();
		}

		// 创建Excel工作表
		WritableWorkbook wwb;

		try{
			Workbook workbook = Workbook.getWorkbook(file);
			wwb = Workbook.createWorkbook(file, workbook);
		}catch (Exception ex){
			ex.printStackTrace();
			wwb = Workbook.createWorkbook(file);
		}

		// 添加第一个工作表并设置第一个Sheet的名字
		if (TextUtils.isEmpty(sheetName)){
			sheetName = "sheet_" + wwb.getNumberOfSheets();
		}
		LogUtil.d(TAG, "sheet NUm = " + wwb.getNumberOfSheets());

		// 判断sheet是否已经存在
		WritableSheet sheet = wwb.getSheet(sheetName);
		if (sheet == null){
			sheet = wwb.createSheet(sheetName, wwb.getNumberOfSheets());
		}

		LogUtil.d(TAG, "sheet Nm = " + wwb.getNumberOfSheets());
		Label label;
		for (int i = 0; i < titles.length; i++) {
			// Label(x,y,z) 代表单元格的第x+1列，第y+1行, 内容z
			// 在Label对象的子对象中指明单元格的位置和内容
			label = new Label(i, 0, titles[i], getHeader());
			// 将定义好的单元格添加到工作表中
			sheet.addCell(label);
		}

		String[] values;
		for (int i = 0; i < valuesList.size(); i++) {
			values = valuesList.get(i);
			for ( int j = 0; j < values.length; j++){
				Label valueLabel = new Label(j , i + 1, values[j], getNormalCell());
				sheet.addCell(valueLabel);
			}
		}

		// 写入数据
		wwb.write();
		// 关闭文件
		wwb.close();
	}

```
