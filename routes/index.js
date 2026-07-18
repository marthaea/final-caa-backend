const router = require('express').Router();

router.use('/auth',          require('./authRoutes'));
router.use('/jobs',          require('./jobRoutes'));
router.use('/applications',  require('./applicationRoutes'));
router.use('/cv',            require('./cvRoutes'));
router.use('/criteria',      require('./criteriaRoutes'));
router.use('/settings',      require('./settingsRoutes'));
router.use('/permissions',   require('./permissionsRoutes'));
router.use('/notifications', require('./notificationsRoutes'));
router.use('/audit',         require('./auditRoutes'));
router.use('/emails',        require('./emailRoutes'));
router.use('/analytics',     require('./analyticsRoutes'));
router.use('/staff',         require('./staffRoutes'));
router.use('/chatbot',       require('./chatbotRoutes'));

module.exports = router;
