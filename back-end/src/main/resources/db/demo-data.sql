-- Simple Todo List Pro 演示数据（幂等）
-- 仅创建演示账号 demo / demo123456（MD5 存储，首次登录自动迁移为 BCrypt）。
-- 仅供本地演示，不要在生产库执行。任务日期相对当前时间生成，可重复执行刷新数据。

DELETE FROM task WHERE user_id = (SELECT user_id FROM (SELECT user_id FROM user WHERE user_name = 'demo') AS t);
DELETE FROM user WHERE user_name = 'demo';
INSERT INTO user (user_name, user_password, task_count) VALUES ('demo', MD5('demo123456'), 0);
SET @demo = LAST_INSERT_ID();

-- 已完成（8 条，含 2 条收藏），截止时间在过去
INSERT INTO task (task_name, status, description, created_time, start_time, remind_time, due_time, user_id, favourite) VALUES
('完成项目周报', 1, '整理本周进展并发送给团队', DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), NULL, DATE_SUB(NOW(), INTERVAL 5 DAY), @demo, 0),
('预约牙医复诊', 1, '确认下午三点的复诊时间', DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, DATE_SUB(NOW(), INTERVAL 4 DAY), @demo, 1),
('读完《原子习惯》第三章', 1, '整理三条可以立刻实践的笔记', DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, DATE_SUB(NOW(), INTERVAL 3 DAY), @demo, 0),
('给家里植物浇水', 1, '阳台的绿萝和龟背竹', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), @demo, 1),
('更新简历', 1, '补充最近一个项目的成果', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), @demo, 0),
('整理桌面文件', 1, '归档到 2026 文件夹', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), @demo, 0),
('回复客户邮件', 1, '确认第二季度的交付时间', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), @demo, 0),
('缴纳水电费', 1, '已在线支付', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), @demo, 0);

-- 未完成（12 条）：3 条逾期、2 条今天、2 条明天、3 条未来数天、2 条无截止
INSERT INTO task (task_name, status, description, created_time, start_time, remind_time, due_time, user_id, favourite) VALUES
('补充产品原型标注', 0, '首页与详情页的交互标注还差一半', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), @demo, 0),
('续订云服务器', 0, '对比三个套餐后选择年度方案', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), @demo, 1),
('修图并上传活动照片', 0, '挑 20 张精修后发到群相册', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), @demo, 0),
('晨跑 5 公里', 0, '从公园东门出发', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 12 HOUR), DATE_SUB(NOW(), INTERVAL 11 HOUR), DATE_ADD(CURDATE(), INTERVAL 20 HOUR), @demo, 0),
('写代码周会记录', 0, '汇总本周合入的分支', DATE_SUB(NOW(), INTERVAL 6 HOUR), DATE_SUB(NOW(), INTERVAL 5 HOUR), DATE_SUB(NOW(), INTERVAL 4 HOUR), DATE_ADD(CURDATE(), INTERVAL 22 HOUR), @demo, 0),
('买周末露营的食材', 0, '牛肉、蔬菜、炭火和一次性餐具', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 10 HOUR), DATE_ADD(CURDATE(), INTERVAL 34 HOUR), @demo, 1),
('预定餐厅座位', 0, '周五晚七点，四人', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 20 HOUR), DATE_SUB(NOW(), INTERVAL 19 HOUR), DATE_ADD(CURDATE(), INTERVAL 38 HOUR), @demo, 0),
('准备面试问题清单', 0, '技术面与行为面各十条', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 72 HOUR), @demo, 0),
('续费健身房年卡', 0, '对比一下续费和停卡方案', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, DATE_ADD(CURDATE(), INTERVAL 108 HOUR), @demo, 0),
('整理年度相册', 0, '按月份归档并备份到硬盘', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, DATE_ADD(CURDATE(), INTERVAL 140 HOUR), @demo, 0),
('学习 TypeScript 泛型', 0, '看官方文档并完成练习', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, NULL, @demo, 0),
('计划一次短途旅行', 0, '江浙沪周边，两天一夜', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL, @demo, 0);

UPDATE user SET task_count = (SELECT COUNT(*) FROM task WHERE user_id = @demo) WHERE user_id = @demo;
