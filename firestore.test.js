const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_focus_123";
const BOB_UID = "bob_focus_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validProfile(uid, ts = new Date(Date.now() - 5000)) {
  return {
    userId: uid,
    displayName: "Jihoo",
    focusMinutes: 25,
    shortBreakMinutes: 5,
    longBreakMinutes: 15,
    dailyGoalMinutes: 120,
    hapticsEnabled: true,
    autoStartBreak: false,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validSession(uid, ts = new Date(Date.now() - 5000)) {
  return {
    userId: uid,
    title: "Compose UI 설계",
    category: "CODING",
    durationMinutes: 25,
    sessionType: "FOCUS",
    dateKey: "2026-11-23",
    createdAt: ts,
  };
}

function validTask(uid, ts = new Date(Date.now() - 5000)) {
  return {
    userId: uid,
    title: "알고리즘 3문제 풀기",
    category: "STUDY",
    estimatedPomodoros: 4,
    completedPomodoros: 0,
    completed: false,
    createdAt: ts,
    updatedAt: ts,
  };
}

// --- 1. UNAUTHENTICATED ACCESS DENIAL ---

test("Unauthenticated user cannot read or write profiles, sessions, or tasks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID)));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("sessions").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("sessions").doc("s1").set(validSession(ALICE_UID))
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("tasks").doc("t1").set(validTask(ALICE_UID))
  );
});

// --- 2. USER PROFILE CRUD & SECURITY ---

test("Owner can create, read, and update profile settings; ghost fields and immutable changes fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(profileRef.set(validProfile(ALICE_UID)));
  await assertSucceeds(profileRef.get());

  // Valid partial settings update
  await assertSucceeds(
    profileRef.update({
      focusMinutes: 30,
      dailyGoalMinutes: 150,
      hapticsEnabled: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field (Shadow Update)
  await assertFails(
    profileRef.update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject createdAt mutation
  await assertFails(
    profileRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject out-of-range duration
  await assertFails(
    profileRef.update({
      focusMinutes: 500,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject future timestamp
  await assertFails(
    profileRef.update({
      focusMinutes: 25,
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Reject listing /users root collection
  await assertFails(aliceDb.collection("users").get());
});

// --- 3. CROSS-USER ISOLATION ---

test("Cross-user reads, lists, writes, and deletes are strictly denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("users").doc(BOB_UID).set(validProfile(BOB_UID));
    await adminDb.collection("users").doc(BOB_UID).collection("sessions").doc("bob_s1").set(validSession(BOB_UID));
    await adminDb.collection("users").doc(BOB_UID).collection("tasks").doc("bob_t1").set(validTask(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).update({ focusMinutes: 10, updatedAt: new Date(Date.now() - 1000) }));
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("sessions").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("sessions").doc("bob_s1").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("sessions").doc("bob_s1").delete());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("tasks").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("tasks").doc("bob_t1").update({ completed: true, updatedAt: new Date(Date.now() - 1000) }));
});

// --- 4. FOCUS SESSIONS (KOTLIN-ALIGNED PAYLOADS & QUERIES) ---

test("Owner can log, list, and delete sessions; updates and invalid payloads are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const sessionsCol = aliceDb.collection("users").doc(ALICE_UID).collection("sessions");

  // Create without optional taskId (matches Kotlin .filterValues { it != null })
  await assertSucceeds(sessionsCol.doc("sess_1").set(validSession(ALICE_UID)));

  // Create with valid optional taskId
  await assertSucceeds(
    sessionsCol.doc("sess_2").set({
      ...validSession(ALICE_UID),
      taskId: "task_abc_1",
      durationMinutes: 45,
    })
  );

  // Kotlin repository query: orderBy createdAt desc
  await assertSucceeds(sessionsCol.orderBy("createdAt", "desc").get());

  // Sessions are append-only: update must fail
  await assertFails(sessionsCol.doc("sess_1").update({ durationMinutes: 99 }));

  // Reject extra @DocumentId 'id' field in write payload
  await assertFails(
    sessionsCol.doc("sess_bad_id").set({
      ...validSession(ALICE_UID),
      id: "sess_bad_id",
    })
  );

  // Reject invalid dateKey format
  await assertFails(
    sessionsCol.doc("sess_bad_date").set({
      ...validSession(ALICE_UID),
      dateKey: "2026/11/23",
    })
  );

  // Reject invalid category enum
  await assertFails(
    sessionsCol.doc("sess_bad_cat").set({
      ...validSession(ALICE_UID),
      category: "GAMING",
    })
  );

  // Reject identity spoofing in userId
  await assertFails(
    sessionsCol.doc("sess_spoof").set({
      ...validSession(BOB_UID),
    })
  );

  // Owner can delete own session
  await assertSucceeds(sessionsCol.doc("sess_1").delete());
});

// --- 5. FOCUS TASKS (ACTION-BASED UPDATES & QUERIES) ---

test("Owner can create, query, edit, toggle, increment, and delete tasks", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const tasksCol = aliceDb.collection("users").doc(ALICE_UID).collection("tasks");

  await assertSucceeds(tasksCol.doc("task_1").set(validTask(ALICE_UID)));

  // Kotlin repository query: orderBy createdAt desc
  await assertSucceeds(tasksCol.orderBy("createdAt", "desc").get());

  // Action 1: Edit task details
  await assertSucceeds(
    tasksCol.doc("task_1").update({
      title: "알고리즘 5문제 풀기",
      category: "CODING",
      estimatedPomodoros: 5,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Action 2: Toggle completion
  await assertSucceeds(
    tasksCol.doc("task_1").update({
      completed: true,
      updatedAt: new Date(Date.now() - 800),
    })
  );

  // Action 3: Increment completedPomodoros by +1
  await assertSucceeds(
    tasksCol.doc("task_1").update({
      completedPomodoros: 1,
      updatedAt: new Date(Date.now() - 600),
    })
  );

  // Reject arbitrary completedPomodoros jump (+5)
  await assertFails(
    tasksCol.doc("task_1").update({
      completedPomodoros: 6,
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Reject mixed/unauthorized field updates (e.g. changing title + completed together outside allowed actions)
  await assertFails(
    tasksCol.doc("task_1").update({
      title: "Hacked",
      completed: false,
      completedPomodoros: 2,
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Reject blank title
  await assertFails(
    tasksCol.doc("task_1").update({
      title: "",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Owner can delete own task
  await assertSucceeds(tasksCol.doc("task_1").delete());
});
