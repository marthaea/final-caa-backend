-- MySQL dump 10.13  Distrib 8.0.46, for Win64 (x86_64)
--
-- Host: tokaido.proxy.rlwy.net    Database: railway
-- ------------------------------------------------------
-- Server version	9.4.0

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- IMPORTANT: This is a raw production data dump extracted from Railway before
-- subscription expiry (2026-08-13). It contains real applicant PII (names,
-- emails, NINs). Treat as confidential. Do not commit to a public repository
-- or share outside the recruitment team.
--
-- For a clean schema-only migration (no candidate data), use
-- scripts/migrate.js instead. Use THIS file only when you specifically need
-- to restore the full historical dataset (jobs, applications, scores, staff,
-- audit log, etc.) onto the new database server.
--

--
-- Table structure for table `analytics_events`
--

DROP TABLE IF EXISTS `analytics_events`;
CREATE TABLE `analytics_events` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `event_type` enum('page_view','job_view','apply_click','save_job','search') NOT NULL,
  `job_id` int unsigned DEFAULT NULL,
  `job_title` varchar(255) DEFAULT NULL,
  `query` varchar(500) DEFAULT NULL,
  `session_id` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1324 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `applications`
--

DROP TABLE IF EXISTS `applications`;
CREATE TABLE `applications` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `job_id` int unsigned NOT NULL,
  `candidate_email` varchar(255) NOT NULL,
  `candidate_name` varchar(255) NOT NULL,
  `abbr` varchar(10) NOT NULL,
  `title` varchar(255) NOT NULL,
  `dept` varchar(100) NOT NULL,
  `date` varchar(50) NOT NULL,
  `status` enum('Pending','Under Review','Shortlisted','Interview','Assessment Scheduled','Assessment Complete','Shortlisted II','Background Check','Offered','Declined','Withdrawn') NOT NULL DEFAULT 'Pending',
  `completion` int NOT NULL DEFAULT '0',
  `cgpa` decimal(3,2) DEFAULT NULL,
  `university` varchar(255) DEFAULT NULL,
  `screening_answers` json DEFAULT NULL,
  `applied_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deployment_station` varchar(255) DEFAULT NULL,
  `deployment_date` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_application` (`job_id`,`candidate_email`),
  CONSTRAINT `applications_ibfk_1` FOREIGN KEY (`job_id`) REFERENCES `jobs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1015 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `assessments`
--

DROP TABLE IF EXISTS `assessments`;
CREATE TABLE `assessments` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `application_id` int unsigned NOT NULL,
  `type` enum('written','psychometric','interview','practical') NOT NULL,
  `scheduled_at` datetime DEFAULT NULL,
  `venue` varchar(255) DEFAULT NULL,
  `scheduled_by` int unsigned DEFAULT NULL,
  `score` decimal(5,2) DEFAULT NULL,
  `passed` tinyint(1) DEFAULT NULL,
  `notes` text,
  `recorded_by` int unsigned DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_app_type` (`application_id`,`type`),
  CONSTRAINT `assessments_ibfk_1` FOREIGN KEY (`application_id`) REFERENCES `applications` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `audit_log`
--

DROP TABLE IF EXISTS `audit_log`;
CREATE TABLE `audit_log` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `actor` varchar(255) NOT NULL,
  `role` varchar(100) NOT NULL,
  `action` varchar(255) NOT NULL,
  `target` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=176 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `candidate_scores`
--

DROP TABLE IF EXISTS `candidate_scores`;
CREATE TABLE `candidate_scores` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `application_id` int unsigned NOT NULL,
  `scorer_user_id` int unsigned NOT NULL,
  `score` decimal(5,2) NOT NULL,
  `comment` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_app_scorer` (`application_id`,`scorer_user_id`),
  KEY `scorer_user_id` (`scorer_user_id`),
  CONSTRAINT `candidate_scores_ibfk_1` FOREIGN KEY (`application_id`) REFERENCES `applications` (`id`) ON DELETE CASCADE,
  CONSTRAINT `candidate_scores_ibfk_2` FOREIGN KEY (`scorer_user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=37 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `chatbot_queries`
--

DROP TABLE IF EXISTS `chatbot_queries`;
CREATE TABLE `chatbot_queries` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `query` varchar(500) NOT NULL,
  `matched_question` varchar(255) DEFAULT NULL,
  `outcome` enum('answered','suggested','fallback') NOT NULL,
  `persona` varchar(20) NOT NULL DEFAULT 'guest',
  `asked_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_outcome_date` (`outcome`,`asked_at`)
) ENGINE=InnoDB AUTO_INCREMENT=27 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `criteria`
--

DROP TABLE IF EXISTS `criteria`;
CREATE TABLE `criteria` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `job_id` int unsigned NOT NULL,
  `min_cgpa` decimal(3,2) DEFAULT NULL,
  `required_keywords` json NOT NULL DEFAULT (_utf8mb4'[]'),
  `notes` text,
  `screening_questions` json DEFAULT NULL,
  `min_experience_years` int DEFAULT NULL,
  `required_qual_level` varchar(50) DEFAULT NULL,
  `disqualifying_universities` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `assessment_types` json DEFAULT NULL,
  `requirements` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `job_id` (`job_id`),
  CONSTRAINT `criteria_ibfk_1` FOREIGN KEY (`job_id`) REFERENCES `jobs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `cv_profiles`
--

DROP TABLE IF EXISTS `cv_profiles`;
CREATE TABLE `cv_profiles` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `user_email` varchar(255) NOT NULL,
  `personal_data` json NOT NULL DEFAULT (_utf8mb4'{}'),
  `highest_level` varchar(50) DEFAULT NULL,
  `qualifications` json NOT NULL DEFAULT (_utf8mb4'[]'),
  `skills` json NOT NULL DEFAULT (_utf8mb4'[]'),
  `experience` json NOT NULL DEFAULT (_utf8mb4'[]'),
  `referees` json NOT NULL DEFAULT (_utf8mb4'[]'),
  `next_of_kin` json NOT NULL DEFAULT (_utf8mb4'{}'),
  `photo_url` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_email` (`user_email`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `departments`
--

DROP TABLE IF EXISTS `departments`;
CREATE TABLE `departments` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `code` varchar(20) NOT NULL,
  `head_user_id` int unsigned DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  KEY `head_user_id` (`head_user_id`),
  CONSTRAINT `departments_ibfk_1` FOREIGN KEY (`head_user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `job_templates`
--

DROP TABLE IF EXISTS `job_templates`;
CREATE TABLE `job_templates` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(150) NOT NULL,
  `department_id` int unsigned DEFAULT NULL,
  `source_job_id` int unsigned DEFAULT NULL,
  `content` json NOT NULL,
  `created_by` int unsigned DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `department_id` (`department_id`),
  KEY `created_by` (`created_by`),
  CONSTRAINT `job_templates_ibfk_1` FOREIGN KEY (`department_id`) REFERENCES `departments` (`id`) ON DELETE SET NULL,
  CONSTRAINT `job_templates_ibfk_2` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `jobs`
--

DROP TABLE IF EXISTS `jobs`;
CREATE TABLE `jobs` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `abbr` varchar(10) NOT NULL,
  `title` varchar(255) NOT NULL,
  `dept` varchar(100) NOT NULL,
  `dept_key` varchar(50) NOT NULL,
  `location` varchar(100) NOT NULL DEFAULT 'Entebbe, Uganda',
  `salary` varchar(100) NOT NULL,
  `salary_band` enum('UG1','UG2','UG3','UG4','UG5','UG6','UG7') NOT NULL,
  `type` enum('Full-time','Contract','Fixed Term Contract') NOT NULL DEFAULT 'Full-time',
  `closes` varchar(50) NOT NULL,
  `closes_at` date NOT NULL,
  `visibility` enum('external','internal','closed') NOT NULL DEFAULT 'external',
  `min_age` int unsigned NOT NULL DEFAULT '21',
  `required_experience` int unsigned NOT NULL DEFAULT '0',
  `required_qualification` varchar(50) NOT NULL,
  `description` text,
  `featured` tinyint(1) NOT NULL DEFAULT '0',
  `created_by` int unsigned DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `status` enum('draft','pending_review','pending_approval','published','declined') NOT NULL DEFAULT 'published',
  `department_id` int unsigned DEFAULT NULL,
  `reviewed_by` int unsigned DEFAULT NULL,
  `approved_by` int unsigned DEFAULT NULL,
  `decline_reason` text,
  `job_ref` varchar(100) DEFAULT NULL,
  `reports_to` varchar(255) DEFAULT NULL,
  `vacancies` int unsigned NOT NULL DEFAULT '1',
  `about_role` text,
  `accountabilities` json DEFAULT NULL,
  `special_skills` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `created_by` (`created_by`),
  CONSTRAINT `jobs_ibfk_1` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=41 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `notifications`
--

DROP TABLE IF EXISTS `notifications`;
CREATE TABLE `notifications` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `recipient_email` varchar(255) NOT NULL,
  `title` varchar(255) NOT NULL,
  `message` text NOT NULL,
  `is_read` tinyint(1) NOT NULL DEFAULT '0',
  `type` enum('shortlisted','declined','interview','offered','info') NOT NULL DEFAULT 'info',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `permission_overrides`
--

DROP TABLE IF EXISTS `permission_overrides`;
CREATE TABLE `permission_overrides` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `admin_id` int unsigned DEFAULT NULL,
  `can_view_applications` tinyint(1) NOT NULL DEFAULT '1',
  `can_shortlist` tinyint(1) NOT NULL DEFAULT '0',
  `can_screen_interns` tinyint(1) NOT NULL DEFAULT '0',
  `can_send_notifications` tinyint(1) NOT NULL DEFAULT '0',
  `can_manage_jobs` tinyint(1) NOT NULL DEFAULT '0',
  `can_manage_criteria` tinyint(1) NOT NULL DEFAULT '0',
  `can_view_staff` tinyint(1) NOT NULL DEFAULT '0',
  `can_export` tinyint(1) NOT NULL DEFAULT '0',
  `can_view_audit` tinyint(1) NOT NULL DEFAULT '0',
  `can_manage_settings` tinyint(1) NOT NULL DEFAULT '0',
  `can_grant_permissions` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `can_review_job` tinyint(1) NOT NULL DEFAULT '0',
  `can_approve_job` tinyint(1) NOT NULL DEFAULT '0',
  `can_manage_departments` tinyint(1) NOT NULL DEFAULT '0',
  `can_manage_admins` tinyint(1) NOT NULL DEFAULT '0',
  `can_assign_rights` tinyint(1) NOT NULL DEFAULT '0',
  `can_schedule_assessment` tinyint(1) NOT NULL DEFAULT '0',
  `can_record_assessment` tinyint(1) NOT NULL DEFAULT '0',
  `email` varchar(255) DEFAULT NULL,
  `role` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `admin_id` (`admin_id`),
  UNIQUE KEY `uniq_email` (`email`),
  CONSTRAINT `permission_overrides_ibfk_1` FOREIGN KEY (`admin_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `sent_emails`
--

DROP TABLE IF EXISTS `sent_emails`;
CREATE TABLE `sent_emails` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `to_email` varchar(255) NOT NULL,
  `candidate_name` varchar(255) NOT NULL,
  `subject` varchar(500) NOT NULL,
  `body` text NOT NULL,
  `trigger_event` varchar(100) NOT NULL,
  `job_title` varchar(255) NOT NULL,
  `sent_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `settings`
--

DROP TABLE IF EXISTS `settings`;
CREATE TABLE `settings` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `org_name` varchar(255) NOT NULL DEFAULT 'Uganda Civil Aviation Authority',
  `email_sender_name` varchar(255) NOT NULL DEFAULT 'CAA HR Team',
  `min_age_threshold` int NOT NULL DEFAULT '21',
  `allow_external_internal_jobs` tinyint(1) NOT NULL DEFAULT '0',
  `session_timeout_minutes` int NOT NULL DEFAULT '30',
  `closing_soon_days` int NOT NULL DEFAULT '7',
  `max_applications_per_candidate` int NOT NULL DEFAULT '5',
  `notif_template_shortlist` text,
  `notif_template_decline` text,
  `notif_template_interview` text,
  `notif_template_offer` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `staff`
--

DROP TABLE IF EXISTS `staff`;
CREATE TABLE `staff` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `employee_number` varchar(50) NOT NULL,
  `first_name` varchar(100) NOT NULL,
  `last_name` varchar(100) NOT NULL,
  `dept` varchar(100) DEFAULT NULL,
  `position` varchar(255) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `joined_date` date DEFAULT NULL,
  `status` varchar(50) NOT NULL DEFAULT 'Active',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `employee_number` (`employee_number`)
) ENGINE=InnoDB AUTO_INCREMENT=31 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `email` varchar(255) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `first_name` varchar(100) NOT NULL,
  `last_name` varchar(100) NOT NULL,
  `account_type` enum('external','internal','admin') NOT NULL DEFAULT 'external',
  `admin_role` enum('super','hr','recruiter','auditor','hr_officer','it_admin','dhra','hod') DEFAULT NULL,
  `employee_number` varchar(50) DEFAULT NULL,
  `effective_type` enum('external','internal','admin') NOT NULL DEFAULT 'external',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `email_verified` tinyint(1) NOT NULL DEFAULT '1',
  `verify_token` varchar(64) DEFAULT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `token_version` int unsigned NOT NULL DEFAULT '0',
  `reset_token_hash` varchar(64) DEFAULT NULL,
  `reset_token_expires` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=27 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- NOTE ON DATA:
-- The original file (recruitment_portal_backup.sql, extracted 2026-08-13) also
-- contains full INSERT statements with real production data for every table
-- above (analytics events, ~1000 applications, audit log, candidate scores,
-- chatbot queries, criteria, cv profiles, departments, job templates, jobs,
-- settings, staff directory, and users with bcrypt password hashes).
--
-- That original file — not this trimmed schema-only copy — is the one to hand
-- to whoever provisions the new database server, since it restores both
-- structure AND all existing data in one `mysql ... < file.sql` import.
-- This trimmed copy is kept in the repo (safe to commit — no PII) purely as a
-- schema reference so the table structure is visible in version control.
--

/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;
/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Schema reference generated from recruitment_portal_backup.sql (2026-08-13)
