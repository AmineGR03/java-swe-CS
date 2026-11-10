// ---------------------------------------------------------------- Q1
function q1(left, right) {
    let count = 0;
    for (let n = left; n <= right; n++) {
        const a = Math.floor(n / 100);
        const b = Math.floor(n / 10) % 10;
        const c = n % 10;
        if (a !== b && b !== c && a !== c) count++;
    }
    return count;
}

// brute reference with Set
function q1ref(left, right) {
    let c = 0;
    for (let n = left; n <= right; n++) {
        const s = String(n);
        if (new Set(s).size === s.length) c++;
    }
    return c;
}

// ---------------------------------------------------------------- Q2
function q2(sensorReadings) {
    return sensorReadings
        .split(' ')
        .map(token => {
            let hasRun = false;
            for (let i = 1; i < token.length; i++) {
                if (token[i] === token[i - 1]) { hasRun = true; break; }
            }
            if (!hasRun) return token;
            let sum = 0;
            for (const ch of token) sum += Number(ch);
            return String(sum);
        })
        .join(' ');
}

// ---------------------------------------------------------------- Q3
function q3(matrix) {
    const rows = matrix.length, cols = matrix[0].length;
    // prefix[r][c] = aggregate over matrix[0..r-1][0..c-1]
    const pSum = Array.from({ length: rows + 1 }, () => new Array(cols + 1).fill(0));
    const pCnt = Array.from({ length: rows + 1 }, () => new Array(cols + 1).fill(0));
    for (let r = 0; r < rows; r++) {
        for (let c = 0; c < cols; c++) {
            const v = matrix[r][c];
            const s = v >= 0 ? v : 0;
            const k = v >= 0 ? 1 : 0;
            pSum[r + 1][c + 1] = s + pSum[r][c + 1] + pSum[r + 1][c] - pSum[r][c];
            pCnt[r + 1][c + 1] = k + pCnt[r][c + 1] + pCnt[r + 1][c] - pCnt[r][c];
        }
    }
    const agg = (r1, c1, r2, c2) => {
        const s = pSum[r2 + 1][c2 + 1] - pSum[r1][c2 + 1] - pSum[r2 + 1][c1] + pSum[r1][c1];
        const k = pCnt[r2 + 1][c2 + 1] - pCnt[r1][c2 + 1] - pCnt[r2 + 1][c1] + pCnt[r1][c1];
        return [s, k];
    };

    let best = Infinity, resRow = -1, resCol = -1;
    for (let cut = 0; cut < rows - 1; cut++) {
        for (let col = 0; col < cols - 1; col++) {
            const quads = [
                agg(0, 0, cut, col),
                agg(0, col + 1, cut, cols - 1),
                agg(cut + 1, 0, rows - 1, col),
                agg(cut + 1, col + 1, rows - 1, cols - 1),
            ];
            if (quads.some(([, k]) => k === 0)) continue;
            let mx = -Infinity, mn = Infinity;
            for (const [s, k] of quads) {
                const avg = Math.floor(s / k);
                if (avg > mx) mx = avg;
                if (avg < mn) mn = avg;
            }
            if (mx - mn < best) { best = mx - mn; resRow = cut; resCol = col; }
        }
    }
    return [resRow, resCol];
}

// naive reference
function q3ref(matrix) {
    const rows = matrix.length, cols = matrix[0].length;
    let best = Infinity, res = [-1, -1];
    for (let r = 0; r < rows - 1; r++) {
        for (let c = 0; c < cols - 1; c++) {
            const boxes = [[0, r, 0, c], [0, r, c + 1, cols - 1], [r + 1, rows - 1, 0, c], [r + 1, rows - 1, c + 1, cols - 1]];
            const avgs = [];
            let ok = true;
            for (const [r1, r2, c1, c2] of boxes) {
                let s = 0, k = 0;
                for (let i = r1; i <= r2; i++) for (let j = c1; j <= c2; j++) if (matrix[i][j] >= 0) { s += matrix[i][j]; k++; }
                if (k === 0) { ok = false; break; }
                avgs.push(Math.floor(s / k));
            }
            if (!ok) continue;
            const spread = Math.max(...avgs) - Math.min(...avgs);
            if (spread < best) { best = spread; res = [r, c]; }
        }
    }
    return res;
}

// ---------------------------------------------------------------- Q4
function q4(lamps) {
    const events = [];
    for (const [pos, radius] of lamps) {
        events.push([pos - radius, 1]);
        events.push([pos + radius + 1, -1]);
    }
    events.sort((a, b) => a[0] - b[0]);

    let active = 0, bestCount = 0, bestCoord = 0;
    for (let i = 0; i < events.length;) {
        const x = events[i][0];
        while (i < events.length && events[i][0] === x) { active += events[i][1]; i++; }
        if (active > bestCount) { bestCount = active; bestCoord = x; }
    }
    return bestCoord;
}

function q4ref(lamps) {
    const map = new Map();
    for (const [p, r] of lamps) for (let x = p - r; x <= p + r; x++) map.set(x, (map.get(x) || 0) + 1);
    let bc = -1, bx = 0;
    for (const x of [...map.keys()].sort((a, b) => a - b)) {
        if (map.get(x) > bc) { bc = map.get(x); bx = x; }
    }
    return bx;
}

// ---------------------------------------------------------------- tests
let fails = 0;
const eq = (name, got, want) => {
    const g = JSON.stringify(got), w = JSON.stringify(want);
    if (g !== w) { fails++; console.log(`FAIL ${name}: got ${g} want ${w}`); }
    else console.log(`ok   ${name}: ${g}`);
};

eq('q1 (123,456)', q1(123, 456), q1ref(123, 456));
eq('q1 (101,998)', q1(101, 998), q1ref(101, 998));
eq('q1 (111,111)', q1(111, 111), 0);
eq('q1 (123,123)', q1(123, 123), 1);
for (let a = 101; a < 990; a += 37) for (let b = a; b < 999; b += 53) {
    if (q1(a, b) !== q1ref(a, b)) { fails++; console.log('FAIL q1 random', a, b); }
}

eq('q2 example', q2('123 4558 787'), '123 22 787');
eq('q2 all runs', q2('11 22 33'), '2 4 6');
eq('q2 none', q2('123 456'), '123 456');
eq('q2 single', q2('1000'), '1');
eq('q2 long run', q2('9999999999'), '90');

const m = [[7, 9, -1, -1], [3, 8, -1, -1], [5, 5, 7, -1]];
eq('q3 given', q3(m), [1, 0]);
eq('q3 given vs ref', q3(m), q3ref(m));
// random cross-check
let rnd = 12345;
const rand = () => (rnd = (rnd * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff;
for (let t = 0; t < 400; t++) {
    const R = 2 + Math.floor(rand() * 4), C = 2 + Math.floor(rand() * 4);
    const mm = Array.from({ length: R }, () => Array.from({ length: C }, () => (rand() < 0.3 ? -1 - Math.floor(rand() * 5) : Math.floor(rand() * 20))));
    const a = JSON.stringify(q3(mm)), b = JSON.stringify(q3ref(mm));
    if (a !== b) { fails++; console.log('FAIL q3 random', JSON.stringify(mm), a, b); }
}

eq('q4 simple', q4([[3, 1], [5, 2]]), 3);
eq('q4 single', q4([[0, 1]]), -1);
eq('q4 negatives', q4([[-5, 2], [-4, 1]]), -5);
for (let t = 0; t < 500; t++) {
    const n = 1 + Math.floor(rand() * 6);
    const ls = Array.from({ length: n }, () => [Math.floor(rand() * 40) - 20, 1 + Math.floor(rand() * 5)]);
    if (q4(ls) !== q4ref(ls)) { fails++; console.log('FAIL q4 random', JSON.stringify(ls), q4(ls), q4ref(ls)); }
}

// values used in the write-up figures
console.log('--- figure data ---');
console.log('q1(123,456) =', q1(123, 456), ' q1(101,998) =', q1(101, 998));
console.log('q4([[3,1],[5,2],[9,1],[6,3]]) =', q4([[3, 1], [5, 2], [9, 1], [6, 3]]), 'ref', q4ref([[3, 1], [5, 2], [9, 1], [6, 3]]));
const demo = [[3, 1], [5, 2], [9, 1], [6, 3]];
const cover = new Map();
for (const [p, r] of demo) for (let x = p - r; x <= p + r; x++) cover.set(x, (cover.get(x) || 0) + 1);
console.log('coverage', [...cover.entries()].sort((a, b) => a[0] - b[0]).map(([x, c]) => `${x}:${c}`).join(' '));

console.log(fails === 0 ? 'ALL PASS' : `${fails} FAILURES`);
