/**
 * 临时验证脚本：校验 math_renderer.html 的 Markdown + 公式保护逻辑。
 * 仅用于开发期验证，验证完成后删除。
 */
const fs = require('fs');
const path = require('path');

const ASSETS = 'D:/Projects/androidapk/app/src/main/assets/katex';

// 加载 marked（UMD 包，在 node 下 require 可直接拿到）
const marked = require(path.join(ASSETS, 'marked.min.js'));

// ── 从 html 中提取的函数（保持一致） ──
const MATH_PLACEHOLDER = /%%MATHBLOCK(\d+)%%/g;

function extractMathBlocks(text) {
    const blocks = [];
    const replaced = text.replace(/\$\$[\s\S]+?\$\$|\$[^\$\n]+?\$/g, function (match) {
        blocks.push(match);
        return '%%MATHBLOCK' + (blocks.length - 1) + '%%';
    });
    return { text: replaced, blocks: blocks };
}

function restoreMathBlocks(html, blocks) {
    return html.replace(MATH_PLACEHOLDER, function (match, index) {
        const block = blocks[parseInt(index, 10)];
        return (block === undefined) ? match : block;
    });
}

// 与 math_renderer.html 保持一致：只转义 < 和 &，保留 > 以免破坏 Markdown 引用语法
function escapeHtml(text) {
    return String(text)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;');
}

function render(rawText) {
    const extracted = extractMathBlocks(rawText);
    // 顺序与 math_renderer.html 保持一致：先提取公式 → 再转义 HTML → 最后 Markdown 渲染
    const safeMarkdown = escapeHtml(extracted.text);
    let html = marked.parse(safeMarkdown, { breaks: true, gfm: true });
    return restoreMathBlocks(html, extracted.blocks);
}

// ── 断言 ──
let pass = 0, fail = 0;
function check(name, actual, mustContain, mustNotContain) {
    const okContain = !mustContain || actual.includes(mustContain);
    const okNot = !mustNotContain || !actual.includes(mustNotContain);
    if (okContain && okNot) {
        pass++;
        console.log(`  ✅ ${name}`);
    } else {
        fail++;
        console.log(`  ❌ ${name}`);
        console.log(`     输出: ${JSON.stringify(actual)}`);
        if (!okContain) console.log(`     缺少: ${JSON.stringify(mustContain)}`);
        if (!okNot) console.log(`     不应包含: ${JSON.stringify(mustNotContain)}`);
    }
}

console.log('=== 1. 基础 Markdown ===');
let r = render('# 标题\n\n这是**粗体**文本');
check('标题渲染为 <h1>', r, '<h1');
check('粗体渲染为 <strong>', r, '<strong>粗体</strong>');

r = render('- 项目一\n- 项目二');
check('无序列表渲染为 <ul><li>', r, '<li>');

r = render('> 这是一段引用');
check('引用渲染为 <blockquote>', r, '<blockquote>');

r = render('`inline code`');
check('行内代码渲染为 <code>', r, '<code>');

r = render('1. 第一\n2. 第二');
check('有序列表渲染为 <ol>', r, '<ol>');

console.log('\n=== 2. 公式保护（核心）===');
r = render('公式 $x_1 + y_2$ 结束');
check('公式被保留', r, '$x_1 + y_2$');
check('公式内的下划线未被 Markdown 转成 <em>', r, null, '<em>');

r = render('$$\\frac{a}{b} = c_1$$');
check('块级公式被保留', r, '$$\\frac{a}{b} = c_1$$');
check('块级公式内下划线未被破坏', r, null, '<em>');

r = render('**粗体** 与 $a*b*c$ 共存');
check('粗体仍正常渲染', r, '<strong>粗体</strong>');
check('公式内星号未被当作斜体', r, '$a*b*c$');

r = render('行内 $a_i$ 和 $b_j$ 两个公式');
check('多个公式均被保护', r, '$a_i$');
check('第二个公式也被保护', r, '$b_j$');

console.log('\n=== 3. Markdown 与公式混排 ===');
r = render('# 数学\n\n- 质能方程 $E = mc^2$\n- 欧拉公式 $e^{i\\pi} + 1 = 0$');
check('标题正常', r, '<h1');
check('列表项中的公式被保护', r, '$E = mc^2$');
check('复杂公式被保护', r, '$e^{i\\pi} + 1 = 0$');

console.log('\n=== 4. 纯文本不应被误改 ===');
r = render('这是一段没有任何标记的普通中文文本，包含逗号，和句号。');
check('纯文本原样保留', r, '这是一段没有任何标记的普通中文文本');
check('不产生多余标签', r, null, '<h1');
check('不产生多余列表', r, null, '<li>');

console.log('\n=== 5. 安全性（html:false 应转义标签）===');
r = render('<script>alert(1)</script>');
check('HTML 标签被完全转义（防注入）', r, '&lt;script&gt;');
check('不产生可执行标签', r, null, '<script>');

console.log('\n=== 6. 换行处理（breaks:true）===');
r = render('第一行\n第二行');
check('单换行转为 <br>', r, '<br');

console.log(`\n${'='.repeat(40)}`);
console.log(`结果: ${pass} 通过 / ${fail} 失败`);
process.exit(fail === 0 ? 0 : 1);
