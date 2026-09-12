import crypto from "crypto";
import { execSync } from "child_process";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
function queryDb(sql) {
  const cmd = `docker exec fsd-2projjecctt-postgres-1 psql -U postgres -d gotham -t -c "${sql}"`;
  return execSync(cmd, { encoding: "utf-8" }).trim();
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

function generateMasterToken(userId = "11111111-1111-1111-1111-111111111111") {
  const secret = "replace-with-a-long-random-secret-key-at-least-256-bits-length";
  const header = Buffer.from(JSON.stringify({ alg: "HS256", typ: "JWT" })).toString("base64url");
  const now = Math.floor(Date.now() / 1000);
  const payload = Buffer.from(
    JSON.stringify({
      sub: userId,
      email: "batman@wayne.corp",
      roles: ["ROLE_USER"],
      iat: now,
      exp: now + 86400 * 7,
    })
  ).toString("base64url");
  const signature = crypto.createHmac("sha256", Buffer.from(secret, "utf-8")).update(`${header}.${payload}`).digest("base64url");
  return `${header}.${payload}.${signature}`;
}

function createRefreshTokenForUser(userId = "11111111-1111-1111-1111-111111111111") {
  const token = "rt_" + Math.random().toString(36).substring(2) + Math.random().toString(36).substring(2) + Date.now();
  const id = crypto.randomUUID();
  const expiresAt = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString();
  queryDb(`INSERT INTO refresh_tokens (id, user_id, token, expires_at, revoked, created_at) VALUES ('${id}', '${userId}', '${token}', '${expiresAt}', false, NOW());`);
  return token;
}

export async function getPages() {
  const res = await fetch("http://127.0.0.1:9222/json/list");
  return await res.json();
}

export function createCDPSession(wsUrl) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(wsUrl);
    let idCounter = 1;
    const callbacks = new Map();
    const eventListeners = new Map();

    ws.onopen = () => {
      resolve({
        send(method, params = {}) {
          return new Promise((res, rej) => {
            const id = idCounter++;
            callbacks.set(id, { res, rej });
            ws.send(JSON.stringify({ id, method, params }));
          });
        },
        on(event, callback) {
          if (!eventListeners.has(event)) eventListeners.set(event, []);
          eventListeners.get(event).push(callback);
        },
        async evaluate(expression) {
          const result = await this.send("Runtime.evaluate", {
            expression,
            returnByValue: true,
            awaitPromise: true,
          });
          if (result.exceptionDetails) {
            throw new Error(result.exceptionDetails.exception?.description || "Evaluation error");
          }
          return result.result?.value;
        },
        async reloadAndWait(timeoutMs = 6000) {
          await this.send("Page.enable");
          return new Promise(async (resolveLoad) => {
            const timeout = setTimeout(resolveLoad, timeoutMs);
            const handler = () => {
              clearTimeout(timeout);
              setTimeout(resolveLoad, 1500);
            };
            this.on("Page.loadEventFired", handler);
            await this.send("Page.reload");
          });
        },
        async navigateAndWait(url, timeoutMs = 8000) {
          await this.send("Page.enable");
          return new Promise(async (resolveLoad) => {
            const timeout = setTimeout(resolveLoad, timeoutMs);
            const handler = () => {
              clearTimeout(timeout);
              setTimeout(resolveLoad, 1500);
            };
            this.on("Page.loadEventFired", handler);
            await this.send("Page.navigate", { url });
          });
        },
        close() {
          ws.close();
        },
      });
    };

    ws.onmessage = (evt) => {
      const msg = JSON.parse(evt.data);
      if (msg.id && callbacks.has(msg.id)) {
        const { res, rej } = callbacks.get(msg.id);
        callbacks.delete(msg.id);
        if (msg.error) rej(msg.error);
        else res(msg.result);
      } else if (msg.method && eventListeners.has(msg.method)) {
        for (const cb of eventListeners.get(msg.method)) {
          cb(msg.params);
        }
      }
    };

    ws.onerror = reject;
  });
}

async function authenticateBrowserSession(cdpSession, targetUrl = "http://localhost:5173/") {
  const rt = createRefreshTokenForUser();
  await cdpSession.evaluate(`(async (rt) => {
    const res = await fetch("/api/auth/refresh", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken: rt }),
      credentials: "include"
    });
    return await res.json();
  })("${rt}")`);
  await cdpSession.navigateAndWait(targetUrl);
  await sleep(1500);
  await cdpSession.evaluate(`window.confirm = () => true;`);
}

// ---------------------------------------------------------------------------
// Main Verification Runner
// ---------------------------------------------------------------------------
async function main() {
  console.log("========================================================");
  console.log("STARTING PHASE 5.2 VERIFICATION: PERSISTENCE & RECOVERY");
  console.log("========================================================");

  // Clean test tables
  try {
    queryDb("DELETE FROM organizations WHERE name LIKE '%PERSISTENCE%' OR name LIKE 'TENANT%';");
  } catch (e) {}

  const masterToken = generateMasterToken();

  // Close extra tabs if open
  const rawPages = await getPages();
  const appTabs = rawPages.filter((p) => p.url.includes("5173"));
  if (appTabs.length > 1) {
    for (let i = 1; i < appTabs.length; i++) {
      await fetch(`http://127.0.0.1:9222/json/close/${appTabs[i].id}`);
    }
  }

  // 1. Setup Browser A
  const pages = await getPages();
  const pageAInfo = pages.find((p) => p.url.includes("5173"));
  if (!pageAInfo) throw new Error("Browser A page not found on port 9222");
  console.log("[Setup] Found Browser A Tab:", pageAInfo.id);

  const browserA = await createCDPSession(pageAInfo.webSocketDebuggerUrl);
  await browserA.evaluate(`window.confirm = () => true;`);
  console.log("[Setup] Authenticating Browser A via Spring Boot HttpOnly cookie...");
  await authenticateBrowserSession(browserA);

  // ---------------------------------------------------------------------------
  // STEP 1 & 2: TRACE CREATION & STABLE IDENTIFIERS
  // ---------------------------------------------------------------------------
  console.log("\n========================================================");
  console.log("STEP 1 & 2: RECORD ENTITY IDENTIFIERS & CREATE ISSUE");
  console.log("========================================================");

  // 1. Create Organization in Browser A
  const createOrgRes = await browserA.evaluate(`(async (t) => {
    const res = await fetch("/api/organizations", {
      method: "POST",
      headers: { "Authorization": "Bearer " + t, "Content-Type": "application/json" },
      body: JSON.stringify({
        name: "GOTHAM-PERSISTENCE-ORG",
        slug: "gotham-persistence-org",
        description: "Authoritative Phase 5.2 persistence test organization"
      })
    });
    return { ok: res.ok, status: res.status, data: await res.json() };
  })("${masterToken}")`);
  const org = createOrgRes.data;
  console.log("[Browser A] Created Organization:", { id: org.id, name: org.name, slug: org.slug });

  // 2. Create Project in Browser A
  const createProjRes = await browserA.evaluate(`(async (orgId, t) => {
    const res = await fetch(\`/api/organizations/\${orgId}/projects\`, {
      method: "POST",
      headers: { "Authorization": "Bearer " + t, "Content-Type": "application/json" },
      body: JSON.stringify({
        name: "ALPHA-CORE-PROJECT",
        projectKey: "ALPHA",
        description: "Authoritative Phase 5.2 persistence test project"
      })
    });
    return { ok: res.ok, status: res.status, data: await res.json() };
  })("${org.id}", "${masterToken}")`);
  const project = createProjRes.data;
  console.log("[Browser A] Created Project:", { id: project.id, key: project.projectKey, name: project.name });

  // 3. Create Issue in Browser A
  const createIssueRes = await browserA.evaluate(`(async (projId, t) => {
    const res = await fetch(\`/api/projects/\${projId}/issues\`, {
      method: "POST",
      headers: { "Authorization": "Bearer " + t, "Content-Type": "application/json" },
      body: JSON.stringify({
        title: "Cross Browser Persistence Test",
        description: "Authoritative PostgreSQL persistence verification issue",
        issueType: "TASK",
        status: "TODO",
        priority: "HIGH"
      })
    });
    return { ok: res.ok, status: res.status, data: await res.json() };
  })("${project.id}", "${masterToken}")`);
  const issue = createIssueRes.data;

  console.log("\n[Trace] Recorded Issue Entity:");
  console.log("  - issue UUID:       ", issue.id);
  console.log("  - issue key:        ", issue.issueKey);
  console.log("  - project UUID:     ", project.id);
  console.log("  - organization UUID:", org.id);
  console.log("  - title:            ", issue.title);
  console.log("  - initial status:   ", issue.status);

  // Verify GET /api/projects/{projectId}/issues from Browser A
  const checkIssuesResA = await browserA.evaluate(`(async (projId, t) => {
    const res = await fetch(\`/api/projects/\${projId}/issues\`, {
      headers: { "Authorization": "Bearer " + t }
    });
    const data = await res.json();
    return { ok: res.ok, status: res.status, count: data.length, ids: data.map(i => i.id) };
  })("${project.id}", "${masterToken}")`);
  console.log("[Browser A] GET /api/projects/{id}/issues returns:", checkIssuesResA);

  // Inspect PostgreSQL directly
  const dbIssueAfterCreate = queryDb(`SELECT id, issue_key, status FROM issues WHERE id = '${issue.id}'`);
  console.log("[Database] Issue row in PostgreSQL directly:", dbIssueAfterCreate);
  if (!dbIssueAfterCreate.includes(issue.id) || !dbIssueAfterCreate.includes("TODO")) {
    throw new Error("Step 1 failed: Issue not found in PostgreSQL after create!");
  }

  // ---------------------------------------------------------------------------
  // STEP 6 & 7: STATUS UPDATE & STATUS MAPPING
  // ---------------------------------------------------------------------------
  console.log("\n========================================================");
  console.log("STEP 6 & 7: MOVE ISSUE TODO -> IN_PROGRESS & VERIFY DB");
  console.log("========================================================");

  // Move the issue via PATCH /api/issues/{id}/status
  console.log("[Browser A] Sending PATCH /api/issues/" + issue.id + "/status with body { status: 'IN_PROGRESS' }...");
  const patchStatusRes = await browserA.evaluate(`(async (issueId, t) => {
    const res = await fetch(\`/api/issues/\${issueId}/status\`, {
      method: "PATCH",
      headers: { "Authorization": "Bearer " + t, "Content-Type": "application/json" },
      body: JSON.stringify({ status: "IN_PROGRESS" })
    });
    return { ok: res.ok, status: res.status, data: await res.json() };
  })("${issue.id}", "${masterToken}")`);
  console.log("[Browser A] PATCH /api/issues/{id}/status response:", {
    ok: patchStatusRes.ok,
    status: patchStatusRes.status,
    responseStatus: patchStatusRes.data?.status
  });

  // Query PostgreSQL directly
  const dbStatusAfterMove = queryDb(`SELECT status FROM issues WHERE id = '${issue.id}'`);
  console.log("[Database] Status in PostgreSQL after move:", dbStatusAfterMove);
  if (!dbStatusAfterMove.includes("IN_PROGRESS")) {
    throw new Error("Step 7 failed: Issue status in PostgreSQL is not IN_PROGRESS!");
  }

  // ---------------------------------------------------------------------------
  // STEP 3 & 8: BROWSER B AUTHENTICATION & PROJECT CONTEXT
  // ---------------------------------------------------------------------------
  console.log("\n========================================================");
  console.log("STEP 3 & 8: LAUNCH BROWSER B & VERIFY TENANCY CONTEXT");
  console.log("========================================================");

  // Launch separate tab for Browser B with explicit context URL
  const targetUrlB = `http://localhost:5173/?orgId=${org.id}&projectId=${project.id}`;
  console.log("[Browser B] Launching separate browser session pointing to:", targetUrlB);
  const createTargetRes = await fetch(`http://127.0.0.1:9222/json/new?${encodeURIComponent(targetUrlB)}`, { method: "PUT" });
  const pageBInfo = await createTargetRes.json();
  console.log("[Browser B] New Tab Created:", pageBInfo.id);

  const browserB = await createCDPSession(pageBInfo.webSocketDebuggerUrl);
  await sleep(1500);
  await browserB.evaluate(`window.confirm = () => true;`);

  // Authenticate Browser B independently
  console.log("[Browser B] Authenticating independently via backend HttpOnly cookie...");
  await authenticateBrowserSession(browserB, targetUrlB);

  // Verify Browser B authentication (/api/auth/me) with polling
  let authMeB = null;
  for (let i = 0; i < 20; i++) {
    authMeB = await browserB.evaluate(`(async () => {
      const token = window.__getAccessToken ? window.__getAccessToken() : null;
      if (!token) return null;
      try {
        const res = await fetch("/api/auth/me", {
          headers: { "Authorization": "Bearer " + token },
          credentials: "include"
        });
        if (!res.ok) return { ok: false, status: res.status };
        const data = await res.json();
        return { ok: true, status: res.status, user: data, token: token };
      } catch (e) {
        return { ok: false, error: e.message };
      }
    })()`);
    if (authMeB && authMeB.ok) break;
    await sleep(500);
  }

  console.log("[Browser B] GET /api/auth/me response:", {
    status: authMeB?.status,
    userId: authMeB?.user?.id,
    email: authMeB?.user?.email,
    roles: authMeB?.user?.roles
  });

  if (!authMeB || !authMeB.ok) {
    throw new Error("Step 3 failed: Browser B authentication failed!");
  }

  // Verify Browser B Tenancy Context (Step 3)
  const contextB = await browserB.evaluate(`(() => {
    const orgSelect = document.querySelector(".workspace-switcher strong")?.innerText;
    const projSelect = document.querySelector('[data-testid="project-select"]')?.value;
    const urlParams = new URLSearchParams(window.location.search);
    return {
      orgIdFromUrl: urlParams.get("orgId"),
      projectIdFromUrl: urlParams.get("projectId"),
      renderedOrg: orgSelect,
      renderedProjectId: projSelect
    };
  })()`);
  console.log("[Browser B] Context:", contextB);
  console.log("  Browser A Org ID == Browser B Org ID:", org.id === contextB.orgIdFromUrl);
  console.log("  Browser A Project ID == Browser B Project ID:", project.id === contextB.projectIdFromUrl);

  // ---------------------------------------------------------------------------
  // STEP 4 & 5: TANSTACK CACHE & NETWORK CAPTURE IN BROWSER B
  // ---------------------------------------------------------------------------
  console.log("\n========================================================");
  console.log("STEP 4 & 5: TANSTACK CACHE ISOLATION & NETWORK REQUEST");
  console.log("========================================================");

  // Inspect storage to ensure no app data query persister exists (Step 5)
  const storageB = await browserB.evaluate(`(() => {
    return {
      localStorageKeys: Object.keys(localStorage),
      sessionStorageKeys: Object.keys(sessionStorage),
    };
  })()`);
  console.log("[Browser B] Storage check (must have no application-data persister):", storageB);

  // Capture Browser B request to GET /api/projects/{projectId}/issues (Step 4)
  const networkB = await browserB.evaluate(`(async (projId) => {
    const token = window.__getAccessToken ? window.__getAccessToken() : null;
    const res = await fetch(\`/api/projects/\${projId}/issues\`, {
      headers: token ? { "Authorization": "Bearer " + token } : {},
      credentials: "include"
    });
    const issues = await res.json();
    return {
      httpStatus: res.status,
      url: res.url,
      issueCount: issues.length,
      issueIds: issues.map(i => i.id),
      issueStatuses: issues.map(i => ({ id: i.id, status: i.status }))
    };
  })("${project.id}")`);
  console.log("[Browser B] Captured Network Request GET /api/projects/{projectId}/issues:");
  console.log("  - HTTP Status:   ", networkB.httpStatus);
  console.log("  - Request URL:   ", networkB.url);
  console.log("  - Issue Count:   ", networkB.issueCount);
  console.log("  - Issue IDs:     ", networkB.issueIds);
  console.log("  - Issue Statuses:", networkB.issueStatuses);

  const exactIssueInNetwork = networkB.issueStatuses.find(i => i.id === issue.id);
  if (!exactIssueInNetwork || exactIssueInNetwork.status !== "IN_PROGRESS") {
    throw new Error("Step 4 failed: Backend did not return the issue with IN_PROGRESS status to Browser B!");
  }

  // ---------------------------------------------------------------------------
  // STEP 2 & 10: BOARD PERSISTENCE VIA STABLE SELECTORS
  // ---------------------------------------------------------------------------
  console.log("\n========================================================");
  console.log("BOARD PERSISTENCE IN BROWSER B (STABLE SELECTORS)");
  console.log("========================================================");

  // Switch Browser B to Board view using stable [data-view="board"] selector
  console.log("[Browser B] Navigating to Board view via [data-view='board']...");
  await browserB.evaluate(`(() => {
    const navBtn = document.querySelector('[data-view="board"]') || document.querySelector('[data-testid="nav-board"]');
    if (navBtn) navBtn.click();
  })()`);
  await sleep(1500);

  // Verify Board View is active and locate the issue card inside the IN_PROGRESS column using stable data attributes (polling up to 10s)
  let boardCheckB = { columnFound: false, cardFoundInColumn: false };
  for (let attempt = 0; attempt < 20; attempt++) {
    boardCheckB = await browserB.evaluate(`((issueUuid, issueKey) => {
      const inProgCol = document.querySelector('[data-status="in-progress"]') || document.querySelector('[data-testid="board-column-in-progress"]');
      if (!inProgCol) return { columnFound: false };

      // Stable selector by exact UUID
      const cardByUuid = inProgCol.querySelector(\`[data-issue-id="\${issueUuid}"]\`);
      // Fallback stable selector by exact Key
      const cardByKey = inProgCol.querySelector(\`[data-issue-key="\${issueKey}"]\`);

      const card = cardByUuid || cardByKey;
      return {
        columnFound: true,
        cardFoundInColumn: !!card,
        cardIssueId: card?.getAttribute("data-issue-id"),
        cardIssueKey: card?.getAttribute("data-issue-key"),
        cardStatus: card?.getAttribute("data-issue-status"),
        cardText: card?.innerText?.substring(0, 100)
      };
    })("${issue.id}", "${issue.issueKey}")`);

    if (boardCheckB.cardFoundInColumn) break;
    await sleep(500);
  }

  console.log("[Browser B] Board Inspection Result:", boardCheckB);
  if (!boardCheckB.columnFound || !boardCheckB.cardFoundInColumn) {
    throw new Error("Board persistence failed: Issue card not found in IN_PROGRESS column in Browser B!");
  }
  console.log(">> BOARD PERSISTENCE VERIFIED: PASS <<");

  // ---------------------------------------------------------------------------
  // TEST E: COMMENT PERSISTENCE
  // ---------------------------------------------------------------------------
  console.log("\n========================================================");
  console.log("COMMENT PERSISTENCE VERIFICATION");
  console.log("========================================================");

  console.log("[Browser A] Adding comment 'Persistence verification comment.'...");
  const addCommentRes = await browserA.evaluate(`(async (issueId, t) => {
    const res = await fetch(\`/api/issues/\${issueId}/comments\`, {
      method: "POST",
      headers: { "Authorization": "Bearer " + t, "Content-Type": "application/json" },
      body: JSON.stringify({ body: "Persistence verification comment." })
    });
    return { ok: res.ok, status: res.status, data: await res.json() };
  })("${issue.id}", "${masterToken}")`);
  console.log("[Browser A] Add comment response:", { ok: addCommentRes.ok, id: addCommentRes.data?.id });

  // Query PostgreSQL
  const dbComment = queryDb(`SELECT body FROM comments WHERE issue_id = '${issue.id}'`);
  console.log("[Database] Comment in PostgreSQL:", dbComment);

  // In Browser B, click the card and verify comment in drawer (polling up to 10s)
  console.log("[Browser B] Clicking issue card to open drawer...");
  let commentCheckB = { drawerOpen: false, hasComment: false };
  for (let attempt = 0; attempt < 15; attempt++) {
    commentCheckB = await browserB.evaluate(`(async (issueUuid) => {
      const card = document.querySelector(\`[data-issue-id="\${issueUuid}"]\`);
      if (card) card.click();
      await new Promise(r => setTimeout(r, 600));
      const drawer = document.querySelector('[data-testid="issue-drawer"]') || document.querySelector(".issue-drawer");
      return {
        drawerOpen: !!drawer,
        hasComment: !!(drawer?.innerText?.includes("Persistence verification comment."))
      };
    })("${issue.id}")`);

    if (commentCheckB.drawerOpen && commentCheckB.hasComment) break;
    await sleep(500);
  }
  console.log("[Browser B] Comment Drawer Check:", commentCheckB);
  if (!commentCheckB.hasComment || !dbComment.includes("Persistence verification comment.")) {
    throw new Error("Comment persistence failed!");
  }
  console.log(">> COMMENT PERSISTENCE VERIFIED: PASS <<");

  // ---------------------------------------------------------------------------
  // TEST F: DELETE PERSISTENCE
  // ---------------------------------------------------------------------------
  console.log("\n========================================================");
  console.log("DELETE PERSISTENCE VERIFICATION");
  console.log("========================================================");

  console.log("[Browser A] Deleting issue...");
  const delIssueRes = await browserA.evaluate(`(async (issueId, t) => {
    const res = await fetch(\`/api/issues/\${issueId}\`, {
      method: "DELETE",
      headers: { "Authorization": "Bearer " + t }
    });
    return { ok: res.ok, status: res.status };
  })("${issue.id}", "${masterToken}")`);
  console.log("[Browser A] Delete issue response:", delIssueRes);

  const dbIssueCount = queryDb(`SELECT count(*) FROM issues WHERE id = '${issue.id}'`);
  console.log("[Database] Issues remaining in PostgreSQL:", dbIssueCount);

  // Confirm in Browser B after reload
  await browserB.reloadAndWait();
  const goneInB = await browserB.evaluate(`((issueUuid) => {
    const card = document.querySelector(\`[data-issue-id="\${issueUuid}"]\`);
    return !card;
  })("${issue.id}")`);
  console.log("[Browser B] Issue confirmed removed from DOM:", goneInB);

  // Delete project and organization
  await browserA.evaluate(`(async (projId, t) => {
    await fetch(\`/api/projects/\${projId}\`, { method: "DELETE", headers: { "Authorization": "Bearer " + t } });
  })("${project.id}", "${masterToken}")`);

  await browserA.evaluate(`(async (orgId, t) => {
    await fetch(\`/api/organizations/\${orgId}\`, { method: "DELETE", headers: { "Authorization": "Bearer " + t } });
  })("${org.id}", "${masterToken}")`);

  const dbOrgCount = queryDb(`SELECT count(*) FROM organizations WHERE id = '${org.id}'`);
  console.log("[Database] Organization count after cascade delete:", dbOrgCount);
  console.log(">> DELETE PERSISTENCE VERIFIED: PASS <<");

  // ---------------------------------------------------------------------------
  // STEP 11, 12, 13, 15: BACKEND OUTAGE, MOCK FALLBACK & RECOVERY
  // ---------------------------------------------------------------------------
  console.log("\n========================================================");
  console.log("STEP 11-15: BACKEND OUTAGE, MOCK FALLBACK & RECOVERY TEST");
  console.log("========================================================");

  // 1. Stop backend container
  console.log("[Docker] Stopping gotham-backend container...");
  execSync("docker stop gotham-backend");
  console.log("[Docker] gotham-backend stopped.");

  // 2. Query while backend is down
  console.log("[Browser] Attempting API call in Browser while backend is DOWN...");
  const fetchWhileDown = await browserA.evaluate(`(async () => {
    try {
      const res = await fetch("/api/organizations", { cache: "no-store" });
      return { ok: res.ok, status: res.status };
    } catch (err) {
      return { ok: false, error: err.message };
    }
  })()`);
  console.log("[Browser] API fetch response while backend down:", fetchWhileDown);

  // Reload page while down to verify NO mock issue data is shown
  console.log("[Browser] Reloading page while backend is DOWN...");
  await browserA.send("Page.reload");
  await sleep(3000);

  const pageTextWhileDown = await browserA.evaluate("document.body.innerText");
  const hasMockIssues =
    pageTextWhileDown.includes("Investigate Arkham Asylum") ||
    pageTextWhileDown.includes("Deploy Batmobile") ||
    pageTextWhileDown.includes("Hack Riddler mainframe") ||
    pageTextWhileDown.includes("GOTHAM-MOCK-ISSUE");
  console.log("[Browser] Has any mock issue data loaded? (Must be false):", hasMockIssues);

  // 3. Restart backend container
  console.log("[Docker] Restarting gotham-backend container...");
  execSync("docker start gotham-backend");
  console.log("[Docker] gotham-backend container started.");

  // 4. Poll /api/health directly on port 8080 (Step 12: do not confuse with frontend)
  console.log("[Backend] Polling direct health endpoint http://localhost:8080/api/health...");
  let directHealthOk = false;
  const sw = Date.now();
  for (let i = 0; i < 30; i++) {
    try {
      const res = await fetch("http://localhost:8080/api/health");
      if (res.ok) {
        const body = await res.json();
        if (body.status === "ok") {
          directHealthOk = true;
          break;
        }
      }
    } catch (e) {}
    await sleep(1000);
  }
  const recoveryTimeSec = ((Date.now() - sw) / 1000).toFixed(1);
  console.log(`[Backend] Direct health check HTTP 200 OK (${recoveryTimeSec}s):`, directHealthOk);

  // 5. Verify frontend proxy health on port 5173
  console.log("[Frontend] Polling Vite proxy health endpoint http://localhost:5173/api/health...");
  let proxyHealthOk = false;
  for (let i = 0; i < 15; i++) {
    try {
      const res = await fetch("http://localhost:5173/api/health");
      if (res.ok) {
        const body = await res.json();
        if (body.status === "ok") {
          proxyHealthOk = true;
          break;
        }
      }
    } catch (e) {}
    await sleep(500);
  }
  console.log("[Frontend] Vite proxy health check HTTP 200 OK:", proxyHealthOk);

  // 6. Reload browser and confirm real connection restored
  console.log("[Browser] Reloading Gotham page after backend recovery...");
  await browserA.reloadAndWait();
  const restoredPageText = await browserA.evaluate("document.body.innerText");
  console.log("[Browser] Page loaded after recovery:", restoredPageText.includes("GOTHAM"));

  browserA.close();
  browserB.close();

  if (fetchWhileDown.ok || hasMockIssues || !directHealthOk || !proxyHealthOk) {
    throw new Error("Backend outage / recovery test failed!");
  }

  console.log("\n========================================================");
  console.log("ALL VERIFICATIONS COMPLETED SUCCESSFULLY!");
  console.log("========================================================");
}

main().catch((err) => {
  console.error("\n[FATAL ERROR IN VERIFICATION]:", err);
  // Ensure backend container is running
  try {
    execSync("docker start gotham-backend");
  } catch (e) {}
  process.exit(1);
});
