const db = require("../config/db");

async function seedShortlistedIIData() {
  try {
    console.log("Seeding Shortlisted II data with multi-admin scores...\n");

    // Get admin user IDs
    const [admins] = await db.query(
      `SELECT id, first_name, email FROM users WHERE account_type = 'admin' ORDER BY id LIMIT 3`
    );

    if (admins.length < 2) {
      console.error("❌ Need at least 2 admin users. Found:", admins.length);
      return;
    }

    console.log("📋 Admin users found:");
    admins.forEach((a) => console.log(`  - ${a.first_name} (ID: ${a.id}, ${a.email})`));

    // Get some existing applications to promote to Shortlisted II
    const [applications] = await db.query(
      `SELECT id, candidate_name, candidate_email, job_id FROM applications WHERE status = 'Shortlisted' LIMIT 5`
    );

    if (applications.length === 0) {
      console.log("❌ No Shortlisted applications found to promote to Shortlisted II");
      return;
    }

    console.log(`\n📊 Found ${applications.length} Shortlisted candidates to promote:\n`);

    // Promote applications to Shortlisted II
    for (const app of applications) {
      await db.query(
        `UPDATE applications SET status = 'Shortlisted II', updated_at = NOW() WHERE id = ?`,
        [app.id]
      );
      console.log(`  ✓ ${app.candidate_name} (App ID: ${app.id})`);
    }

    console.log(
      `\n⭐ Adding multi-admin scores and comments for Shortlisted II candidates...\n`
    );

    // Scoring data: realistic scores (60-95 range) with detailed comments
    const scoringData = [
      {
        appIndex: 0,
        scores: [
          { scorerIndex: 0, score: 85, comment: "Strong technical foundation. Excellent problem-solving skills demonstrated in interview questions." },
          { scorerIndex: 1, score: 82, comment: "Good communication. Experience is directly relevant. Minor gaps in specialized tool knowledge." },
          { scorerIndex: 2, score: 88, comment: "Outstanding candidate. Leadership qualities evident. Highly recommended for advancement." }
        ]
      },
      {
        appIndex: 1,
        scores: [
          { scorerIndex: 0, score: 78, comment: "Meets core requirements. Shows promise but needs more hands-on experience in this domain." },
          { scorerIndex: 1, score: 81, comment: "Good attitude and willingness to learn. Interview responses were thoughtful and well-structured." },
          { scorerIndex: 2, score: 79, comment: "Solid candidate. Comparable skills to other shortlisted applicants. Further development recommended." }
        ]
      },
      {
        appIndex: 2,
        scores: [
          { scorerIndex: 0, score: 90, comment: "Exceptional! Best candidate in this pool. Demonstrates mastery of key competencies." },
          { scorerIndex: 1, score: 89, comment: "Impressive track record and relevant certifications. Culture fit is excellent." },
          { scorerIndex: 2, score: 92, comment: "Highly impressive. Ready for senior responsibilities. Recommend for accelerated track." }
        ]
      },
      {
        appIndex: 3,
        scores: [
          { scorerIndex: 0, score: 72, comment: "Meets minimum standards. Lacks some specific experience areas but willing to upskill." },
          { scorerIndex: 1, score: 75, comment: "Average performance. Would benefit from additional technical training before role start." },
          { scorerIndex: 2, score: 74, comment: "Adequate but not standout. Consider for support role rather than lead position." }
        ]
      },
      {
        appIndex: 4,
        scores: [
          { scorerIndex: 1, score: 86, comment: "Excellent fit for the role. Demonstrated deep domain expertise and collaborative approach." },
          { scorerIndex: 2, score: 87, comment: "Strong performer. Interview questions answered comprehensively. Ready to start immediately." }
        ]
      }
    ];

    // Insert scores
    let totalScoresAdded = 0;
    for (const scoreRecord of scoringData) {
      const app = applications[scoreRecord.appIndex];
      if (!app) continue;

      for (const scoreData of scoreRecord.scores) {
        const scorer = admins[scoreData.scorerIndex];
        if (!scorer) continue;

        const [existing] = await db.query(
          `SELECT id FROM candidate_scores WHERE application_id = ? AND scorer_user_id = ?`,
          [app.id, scorer.id]
        );

        if (existing.length === 0) {
          await db.query(
            `INSERT INTO candidate_scores (application_id, scorer_user_id, score, comment, created_at, updated_at)
             VALUES (?, ?, ?, ?, NOW(), NOW())`,
            [app.id, scorer.id, scoreData.score, scoreData.comment]
          );
          totalScoresAdded++;
          console.log(
            `  ✓ ${app.candidate_name} scored ${scoreData.score}/100 by ${scorer.first_name}`
          );
        }
      }
    }

    console.log(
      `\n✅ Seeding complete! Added ${totalScoresAdded} scores from ${admins.length} admins.\n`
    );

    // Show summary
    const [summary] = await db.query(
      `SELECT
        a.id,
        a.candidate_name,
        COUNT(cs.id) as score_count,
        ROUND(AVG(cs.score), 1) as avg_score,
        MIN(cs.score) as min_score,
        MAX(cs.score) as max_score
      FROM applications a
      LEFT JOIN candidate_scores cs ON a.id = cs.application_id
      WHERE a.status = 'Shortlisted II'
      GROUP BY a.id, a.candidate_name
      ORDER BY a.id`
    );

    console.log("📊 Shortlisted II Panel Scoring Summary:");
    console.log("─".repeat(70));
    summary.forEach((row) => {
      const avgScore = row.avg_score ? parseFloat(row.avg_score).toFixed(1) : "—";
      console.log(
        `${row.candidate_name.padEnd(30)} | Avg: ${avgScore}/100 | Range: ${row.min_score || "—"}–${row.max_score || "—"} | Scores: ${row.score_count}`
      );
    });
    console.log("─".repeat(70));

  } catch (error) {
    console.error("❌ Error seeding Shortlisted II data:", error.message);
  } finally {
    process.exit(0);
  }
}

seedShortlistedIIData();
