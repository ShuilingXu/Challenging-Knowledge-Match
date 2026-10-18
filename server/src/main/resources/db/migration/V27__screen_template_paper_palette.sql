-- 大屏模板改用与后台工作区一致的纸张配色。
-- 只调整预置模板：管理员自定义的模板保留自己挑选的背景色。
update screen_templates
set components_json = replace(replace(replace(replace(components_json,
    '#6f5960', '#f6e7e3'),
    '#648496', '#e8f0f6'),
    '#836c67', '#f6ece1'),
    '#766b83', '#efeaf2')
where preset = true
  and (components_json like '%#6f5960%'
    or components_json like '%#648496%'
    or components_json like '%#836c67%'
    or components_json like '%#766b83%');
