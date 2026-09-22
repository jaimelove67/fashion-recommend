\set ON_ERROR_STOP on

BEGIN;

INSERT INTO app_users (username, password_hash, enabled)
VALUES ('demo-user', '$2a$10$4vvgBGdouM233ddKN5aFS.y/of5uLi4cLIwYOl0.itFhin6e/s8vu', TRUE)
ON CONFLICT (username) DO UPDATE
SET password_hash = EXCLUDED.password_hash,
    enabled = TRUE;

INSERT INTO app_authorities (username, authority)
VALUES ('demo-user', 'ROLE_USER')
ON CONFLICT (username, authority) DO NOTHING;

-- Development-only management account for demonstrating the protected admin surface.
INSERT INTO app_users (username, password_hash, enabled)
VALUES ('demo-admin', '$2a$10$4vvgBGdouM233ddKN5aFS.y/of5uLi4cLIwYOl0.itFhin6e/s8vu', TRUE)
ON CONFLICT (username) DO UPDATE
SET password_hash = EXCLUDED.password_hash,
    enabled = TRUE;

INSERT INTO app_authorities (username, authority)
VALUES ('demo-admin', 'ROLE_ADMIN')
ON CONFLICT (username, authority) DO NOTHING;

DELETE FROM recommendations WHERE user_id = 'demo-user';
DELETE FROM wardrobe_items WHERE user_id = 'demo-user';
DELETE FROM style_profiles WHERE user_id = 'demo-user';

INSERT INTO wardrobe_items (
    user_id, name, category, color, style, image_url, image_object_key,
    recognition_status, recognition_message, created_at
) VALUES
    ('demo-user', '暖白牛津纺衬衫', '上装', '暖白', '极简通勤', '/assets/wardrobe/wardrobe-01-warm-oxford-shirt.png', NULL, 'MANUAL', NULL, CURRENT_TIMESTAMP - INTERVAL '20 days'),
    ('demo-user', '雾蓝提花针织衫', '上装', '雾蓝', '低饱和休闲', '/assets/wardrobe/wardrobe-02-mist-blue-knit.png', NULL, 'MANUAL', NULL, CURRENT_TIMESTAMP - INTERVAL '19 days'),
    ('demo-user', '深靛直筒牛仔裤', '下装', '深靛蓝', '利落休闲', '/assets/wardrobe/wardrobe-03-graphite-jeans.png', NULL, 'MANUAL', NULL, CURRENT_TIMESTAMP - INTERVAL '18 days'),
    ('demo-user', '橄榄绿 A 字半裙', '下装', '橄榄绿', '松弛复古', '/assets/wardrobe/wardrobe-04-olive-a-line-skirt.jpg', NULL, 'MANUAL', NULL, CURRENT_TIMESTAMP - INTERVAL '17 days'),
    ('demo-user', '炭灰轻量西装', '外套', '石墨灰', '柔和剪裁', '/assets/wardrobe/wardrobe-05-charcoal-jacket.png', NULL, 'MANUAL', NULL, CURRENT_TIMESTAMP - INTERVAL '16 days'),
    ('demo-user', '白色低帮皮质运动鞋', '鞋履', '白色', '城市休闲', '/assets/wardrobe/wardrobe-06-white-low-top-shoes.jpg', NULL, 'MANUAL', NULL, CURRENT_TIMESTAMP - INTERVAL '15 days'),
    ('demo-user', '黑色方头乐福鞋', '鞋履', '黑色', '正式通勤', '/assets/wardrobe/wardrobe-07-black-loafers.png', NULL, 'MANUAL', NULL, CURRENT_TIMESTAMP - INTERVAL '14 days'),
    ('demo-user', '草木绿基础 T 恤', '上装', '草木绿', '轻松休闲', '/assets/wardrobe/wardrobe-09-mist-blue-tshirt.png', NULL, 'MANUAL', NULL, CURRENT_TIMESTAMP - INTERVAL '13 days'),
    ('demo-user', '玫红基础 T 恤', '上装', '玫红', '轻松休闲', '/assets/wardrobe/wardrobe-10-charcoal-tshirt.png', NULL, 'MANUAL', NULL, CURRENT_TIMESTAMP - INTERVAL '12 days'),
    ('demo-user', '驼色直筒大衣', '外套', '驼色', '极简通勤', '/assets/wardrobe/wardrobe-11-sand-short-coat.png', NULL, 'MANUAL', NULL, CURRENT_TIMESTAMP - INTERVAL '11 days'),
    ('demo-user', '浅蓝做旧牛仔裤', '下装', '浅蓝', '城市休闲', '/assets/wardrobe/wardrobe-12-deep-blue-jeans.png', NULL, 'MANUAL', NULL, CURRENT_TIMESTAMP - INTERVAL '10 days'),
    ('demo-user', '中蓝直筒牛仔裤', '下装', '中蓝', '利落休闲', '/assets/wardrobe/wardrobe-13-light-blue-jeans.png', NULL, 'MANUAL', NULL, CURRENT_TIMESTAMP - INTERVAL '9 days'),
    ('demo-user', '浅蓝直筒牛仔裤', '下装', '浅蓝', '低饱和休闲', '/assets/wardrobe/wardrobe-14-black-jeans.png', NULL, 'MANUAL', NULL, CURRENT_TIMESTAMP - INTERVAL '8 days'),
    ('demo-user', '黑色长款半裙', '下装', '黑色', '简约通勤', '/assets/wardrobe/wardrobe-15-navy-skirt.jpg', NULL, 'MANUAL', NULL, CURRENT_TIMESTAMP - INTERVAL '7 days'),
    ('demo-user', '黑灰户外凉鞋', '鞋履', '黑灰', '城市休闲', '/assets/wardrobe/wardrobe-16-brown-flat-sandals.png', NULL, 'MANUAL', NULL, CURRENT_TIMESTAMP - INTERVAL '6 days'),
    ('demo-user', '黑色系带短靴', '鞋履', '黑色', '利落休闲', '/assets/wardrobe/wardrobe-17-black-ankle-boots.png', NULL, 'MANUAL', NULL, CURRENT_TIMESTAMP - INTERVAL '5 days'),
    ('demo-user', '棕色皮带', '配饰', '棕色', '简约通勤', '/assets/wardrobe/wardrobe-18-brown-leather-belt.png', NULL, 'MANUAL', NULL, CURRENT_TIMESTAMP - INTERVAL '4 days'),
    ('demo-user', '炭灰针织围巾', '配饰', '炭灰', '柔和层次', '/assets/wardrobe/wardrobe-19-charcoal-knit-scarf.png', NULL, 'MANUAL', NULL, CURRENT_TIMESTAMP - INTERVAL '3 days'),
    ('demo-user', '米白圆领卫衣', '上装', '米白', '低饱和休闲', '/assets/wardrobe/wardrobe-20-ivory-sweater.png', NULL, 'MANUAL', NULL, CURRENT_TIMESTAMP - INTERVAL '2 days');

-- This row intentionally enters the same failure state produced when vision recognition is unavailable.
INSERT INTO wardrobe_items (
    user_id, name, category, color, style, image_url, image_object_key,
    recognition_status, recognition_message, created_at
) VALUES (
    'demo-user', '待补充衣物', '待识别', '待识别', NULL, '/assets/wardrobe/wardrobe-08-mist-blue-striped-shirt.png', NULL,
    'NEEDS_MANUAL_REVIEW', '图片识别未得到完整信息，请手动确认类别、颜色和名称',
    CURRENT_TIMESTAMP - INTERVAL '1 day'
);

-- Simulate the user completing the correction form after the failed recognition.
UPDATE wardrobe_items
SET name = '雾蓝条纹衬衫',
    category = '上装',
    color = '雾蓝',
    style = '清爽层次',
    recognition_status = 'MANUAL_CORRECTED',
    recognition_message = NULL
WHERE user_id = 'demo-user'
  AND recognition_status = 'NEEDS_MANUAL_REVIEW'
  AND name = '待补充衣物';

INSERT INTO style_profiles (
    user_id, display_name, gender, style_preferences, color_preferences, occasion_preferences,
    style_tags, try_style_tags, color_suggestions, item_suggestions,
    reason_summary, model_name, updated_at
) VALUES (
    'demo-user',
    '林知夏',
    'FEMALE',
    '["极简通勤","低饱和休闲"]',
    '["暖白","雾蓝","石墨灰"]',
    '["通勤","答辩汇报","周末出行"]',
    '["清爽层次","柔和剪裁","低饱和"]',
    '["轻机能","松弛复古"]',
    '["雾蓝与暖白","石墨灰与橄榄绿"]',
    '["轻量西装","直筒牛仔裤","低帮运动鞋"]',
    '现有衣橱以低饱和中性色为主，适合用清晰轮廓完成通勤与汇报场景，再通过雾蓝或橄榄绿增加层次。',
    'demo-seed-v1',
    CURRENT_TIMESTAMP
);

INSERT INTO recommendations (
    user_id, occasion, city, temperature_c, summary, reason, engine,
    weather_apparent_temperature_c, weather_precipitation_mm, weather_code,
    weather_wind_speed_kmh, weather_observed_at, weather_source, saved, created_at
) VALUES
    (
        'demo-user', '答辩汇报', '杭州', 24.0,
        '答辩汇报推荐：暖白牛津纺衬衫、深靛直筒牛仔裤、炭灰轻量西装、黑色方头乐福鞋',
        '暖白上装提亮面部，炭灰西装与深靛直筒牛仔裤保持利落度，方头乐福鞋让整体收束。24°C 室内外温差下可灵活脱下外套。',
        'development-rule-v1', 25.0, 0.0, 1, 9.0,
        CURRENT_TIMESTAMP - INTERVAL '2 hours', 'demo-snapshot', TRUE, CURRENT_TIMESTAMP - INTERVAL '2 hours'
    ),
    (
        'demo-user', '周末城市漫步', '杭州', 27.0,
        '周末城市漫步推荐：雾蓝提花针织衫、橄榄绿 A 字半裙、白色低帮皮质运动鞋',
        '雾蓝和橄榄绿保持低饱和协调，A 字半裙留出活动余量，白色运动鞋适合较长时间步行。',
        'development-rule-v1', 28.5, 0.2, 2, 11.0,
        CURRENT_TIMESTAMP - INTERVAL '1 day', 'demo-snapshot', FALSE, CURRENT_TIMESTAMP - INTERVAL '1 day'
    ),
    (
        'demo-user', '日常通勤', '杭州', 21.0,
        '日常通勤推荐：雾蓝条纹衬衫、深靛直筒牛仔裤、黑色方头乐福鞋',
        '人工确认后的雾蓝条纹衬衫加入推荐，搭配深靛直筒牛仔裤和黑色乐福鞋，在清爽层次与通勤秩序之间取得平衡。',
        'development-rule-v1', 20.0, 1.4, 61, 14.0,
        CURRENT_TIMESTAMP - INTERVAL '3 days', 'demo-snapshot', FALSE, CURRENT_TIMESTAMP - INTERVAL '3 days'
    );

INSERT INTO recommendation_items (
    recommendation_id, position_no, wardrobe_item_id, name, category, color, style, image_url, created_at
) VALUES
    ((SELECT id FROM recommendations WHERE user_id = 'demo-user' AND occasion = '答辩汇报'), 0,
     (SELECT id FROM wardrobe_items WHERE user_id = 'demo-user' AND name = '暖白牛津纺衬衫'),
     '暖白牛津纺衬衫', '上装', '暖白', '极简通勤', '/assets/wardrobe/wardrobe-01-warm-oxford-shirt.png', CURRENT_TIMESTAMP - INTERVAL '2 hours'),
    ((SELECT id FROM recommendations WHERE user_id = 'demo-user' AND occasion = '答辩汇报'), 1,
     (SELECT id FROM wardrobe_items WHERE user_id = 'demo-user' AND name = '深靛直筒牛仔裤'),
     '深靛直筒牛仔裤', '下装', '深靛蓝', '利落休闲', '/assets/wardrobe/wardrobe-03-graphite-jeans.png', CURRENT_TIMESTAMP - INTERVAL '2 hours'),
    ((SELECT id FROM recommendations WHERE user_id = 'demo-user' AND occasion = '答辩汇报'), 2,
     (SELECT id FROM wardrobe_items WHERE user_id = 'demo-user' AND name = '炭灰轻量西装'),
     '炭灰轻量西装', '外套', '石墨灰', '柔和剪裁', '/assets/wardrobe/wardrobe-05-charcoal-jacket.png', CURRENT_TIMESTAMP - INTERVAL '2 hours'),
    ((SELECT id FROM recommendations WHERE user_id = 'demo-user' AND occasion = '答辩汇报'), 3,
     (SELECT id FROM wardrobe_items WHERE user_id = 'demo-user' AND name = '黑色方头乐福鞋'),
     '黑色方头乐福鞋', '鞋履', '黑色', '正式通勤', '/assets/wardrobe/wardrobe-07-black-loafers.png', CURRENT_TIMESTAMP - INTERVAL '2 hours'),
    ((SELECT id FROM recommendations WHERE user_id = 'demo-user' AND occasion = '周末城市漫步'), 0,
     (SELECT id FROM wardrobe_items WHERE user_id = 'demo-user' AND name = '雾蓝提花针织衫'),
     '雾蓝提花针织衫', '上装', '雾蓝', '低饱和休闲', '/assets/wardrobe/wardrobe-02-mist-blue-knit.png', CURRENT_TIMESTAMP - INTERVAL '1 day'),
    ((SELECT id FROM recommendations WHERE user_id = 'demo-user' AND occasion = '周末城市漫步'), 1,
     (SELECT id FROM wardrobe_items WHERE user_id = 'demo-user' AND name = '橄榄绿 A 字半裙'),
     '橄榄绿 A 字半裙', '下装', '橄榄绿', '松弛复古', '/assets/wardrobe/wardrobe-04-olive-a-line-skirt.jpg', CURRENT_TIMESTAMP - INTERVAL '1 day'),
    ((SELECT id FROM recommendations WHERE user_id = 'demo-user' AND occasion = '周末城市漫步'), 2,
     (SELECT id FROM wardrobe_items WHERE user_id = 'demo-user' AND name = '白色低帮皮质运动鞋'),
     '白色低帮皮质运动鞋', '鞋履', '白色', '城市休闲', '/assets/wardrobe/wardrobe-06-white-low-top-shoes.jpg', CURRENT_TIMESTAMP - INTERVAL '1 day'),
    ((SELECT id FROM recommendations WHERE user_id = 'demo-user' AND occasion = '日常通勤'), 0,
     (SELECT id FROM wardrobe_items WHERE user_id = 'demo-user' AND name = '雾蓝条纹衬衫'),
     '雾蓝条纹衬衫', '上装', '雾蓝', '清爽层次', '/assets/wardrobe/wardrobe-08-mist-blue-striped-shirt.png', CURRENT_TIMESTAMP - INTERVAL '3 days'),
    ((SELECT id FROM recommendations WHERE user_id = 'demo-user' AND occasion = '日常通勤'), 1,
     (SELECT id FROM wardrobe_items WHERE user_id = 'demo-user' AND name = '深靛直筒牛仔裤'),
     '深靛直筒牛仔裤', '下装', '深靛蓝', '利落休闲', '/assets/wardrobe/wardrobe-03-graphite-jeans.png', CURRENT_TIMESTAMP - INTERVAL '3 days'),
    ((SELECT id FROM recommendations WHERE user_id = 'demo-user' AND occasion = '日常通勤'), 2,
     (SELECT id FROM wardrobe_items WHERE user_id = 'demo-user' AND name = '黑色方头乐福鞋'),
     '黑色方头乐福鞋', '鞋履', '黑色', '正式通勤', '/assets/wardrobe/wardrobe-07-black-loafers.png', CURRENT_TIMESTAMP - INTERVAL '3 days');

INSERT INTO recommendation_feedback (
    recommendation_id, user_id, rating, feedback_type, comment, updated_at
) VALUES (
    (SELECT id FROM recommendations WHERE user_id = 'demo-user' AND occasion = '答辩汇报'),
    'demo-user', 5, '很满意', '层次清楚、正式度合适，适合作为答辩当天的备选方案。', CURRENT_TIMESTAMP - INTERVAL '90 minutes'
);

COMMIT;

SELECT 'wardrobe_items' AS metric, COUNT(*)::TEXT AS value
FROM wardrobe_items WHERE user_id = 'demo-user'
UNION ALL
SELECT 'recommendations', COUNT(*)::TEXT
FROM recommendations WHERE user_id = 'demo-user'
UNION ALL
SELECT 'saved_with_feedback', COUNT(*)::TEXT
FROM recommendations r
JOIN recommendation_feedback f ON f.recommendation_id = r.id AND f.user_id = r.user_id
WHERE r.user_id = 'demo-user' AND r.saved = TRUE
UNION ALL
SELECT 'manual_corrected', COUNT(*)::TEXT
FROM wardrobe_items
WHERE user_id = 'demo-user' AND recognition_status = 'MANUAL_CORRECTED'
ORDER BY metric;
