const swaggerUi = require('swagger-ui-express');

const spec = {
  openapi: '3.0.3',
  info: {
    title: 'CAA Recruitment API',
    version: '1.0.0',
    description: 'Uganda Civil Aviation Authority — Recruitment Portal REST API',
    contact: { name: 'CAA HR & ICT', email: 'recruitment@caa.co.ug' }
  },
  servers: [
    { url: '/api', description: 'Current server' }
  ],
  components: {
    securitySchemes: {
      bearerAuth: { type: 'http', scheme: 'bearer', bearerFormat: 'JWT' }
    },
    schemas: {
      Error: {
        type: 'object',
        properties: {
          success: { type: 'boolean', example: false },
          error:   { type: 'string' }
        }
      },
      Job: {
        type: 'object',
        properties: {
          id: { type: 'integer' }, abbr: { type: 'string' },
          title: { type: 'string' }, dept: { type: 'string' },
          deptKey: { type: 'string' }, location: { type: 'string' },
          salary: { type: 'string' }, salaryBand: { type: 'string', enum: ['UG1','UG2','UG3','UG4','UG5','UG6','UG7'] },
          type: { type: 'string', enum: ['Full-time','Contract'] },
          closes: { type: 'string' }, closesAt: { type: 'string', format: 'date' },
          visibility: { type: 'string', enum: ['external','internal','closed'] },
          minAge: { type: 'integer' }, requiredExperience: { type: 'integer' },
          requiredQualification: { type: 'string' }, description: { type: 'string' },
          featured: { type: 'boolean' }
        }
      },
      Application: {
        type: 'object',
        properties: {
          id: { type: 'integer' }, jobId: { type: 'integer' },
          abbr: { type: 'string' }, title: { type: 'string' },
          dept: { type: 'string' }, date: { type: 'string' },
          status: { type: 'string', enum: ['Pending','Shortlisted','Interview','Offered','Declined','Withdrawn'] },
          completion: { type: 'integer' }, candidateName: { type: 'string' },
          candidateEmail: { type: 'string', format: 'email' },
          cgpa: { type: 'number', nullable: true },
          university: { type: 'string', nullable: true }
        }
      },
      User: {
        type: 'object',
        properties: {
          id: { type: 'integer' }, email: { type: 'string', format: 'email' },
          firstName: { type: 'string' }, lastName: { type: 'string' },
          accountType: { type: 'string', enum: ['external','internal','admin'] },
          adminRole: { type: 'string', enum: ['super','hr','recruiter'], nullable: true },
          employeeNumber: { type: 'string', nullable: true },
          token: { type: 'string', description: 'JWT access token (2h)' }
        }
      }
    }
  },
  security: [{ bearerAuth: [] }],
  paths: {
    // ── Auth ──────────────────────────────────────────────────────────────────
    '/auth/register': {
      post: {
        tags: ['Auth'], summary: 'Register a new account', security: [],
        requestBody: { required: true, content: { 'application/json': { schema: {
          type: 'object', required: ['email','password','firstName','lastName','accountType'],
          properties: {
            email: { type: 'string', format: 'email' },
            password: { type: 'string', minLength: 8 },
            firstName: { type: 'string' }, lastName: { type: 'string' },
            accountType: { type: 'string', enum: ['external','internal'] },
            employeeNumber: { type: 'string', description: 'Required when accountType=internal' }
          }
        }}}},
        responses: {
          '201': { description: 'Registered — access token in body, refresh token in httpOnly cookie',
            content: { 'application/json': { schema: { properties: { success: { type: 'boolean' }, data: { '$ref': '#/components/schemas/User' } } } } } },
          '400': { description: 'Validation error', content: { 'application/json': { schema: { '$ref': '#/components/schemas/Error' } } } },
          '409': { description: 'Email already registered' }
        }
      }
    },
    '/auth/login': {
      post: {
        tags: ['Auth'], summary: 'Login', security: [],
        requestBody: { required: true, content: { 'application/json': { schema: {
          type: 'object', required: ['email','password'],
          properties: { email: { type: 'string', format: 'email' }, password: { type: 'string' } }
        }}}},
        responses: {
          '200': { description: 'Authenticated — access token in body, refresh token in httpOnly cookie',
            content: { 'application/json': { schema: { properties: { success: { type: 'boolean' }, data: { '$ref': '#/components/schemas/User' } } } } } },
          '401': { description: 'Invalid credentials' }
        }
      }
    },
    '/auth/refresh-token': {
      post: {
        tags: ['Auth'], summary: 'Rotate refresh token and get a new access token', security: [],
        description: 'Reads the `caa_refresh` httpOnly cookie. Issues a new access token and rotates the refresh token cookie.',
        responses: {
          '200': { description: 'New access token', content: { 'application/json': { schema: {
            properties: { success: { type: 'boolean' }, data: { properties: { token: { type: 'string' } } } }
          }}}},
          '401': { description: 'Missing or invalid refresh token' }
        }
      }
    },
    '/auth/me': {
      get: {
        tags: ['Auth'], summary: 'Get current user',
        responses: {
          '200': { description: 'Current user profile' },
          '401': { description: 'Unauthorized' }
        }
      }
    },
    '/auth/profile': {
      put: {
        tags: ['Auth'], summary: 'Update own profile',
        requestBody: { content: { 'application/json': { schema: {
          properties: { firstName: { type: 'string' }, lastName: { type: 'string' }, email: { type: 'string', format: 'email' } }
        }}}},
        responses: { '200': { description: 'Updated profile' } }
      }
    },
    '/auth/logout': {
      post: {
        tags: ['Auth'], summary: 'Logout — clears refresh token cookie',
        responses: { '200': { description: 'Logged out' } }
      }
    },
    // ── Jobs ─────────────────────────────────────────────────────────────────
    '/jobs': {
      get: {
        tags: ['Jobs'], summary: 'List open jobs (visibility filtered by account type)', security: [],
        responses: { '200': { description: 'List of jobs', content: { 'application/json': { schema: {
          properties: { success: { type: 'boolean' }, data: { type: 'array', items: { '$ref': '#/components/schemas/Job' } }, total: { type: 'integer' } }
        }}}}}
      },
      post: {
        tags: ['Jobs'], summary: 'Create a job (admin: canManageJobs)',
        requestBody: { required: true, content: { 'application/json': { schema: { '$ref': '#/components/schemas/Job' } } } },
        responses: { '201': { description: 'Created job' }, '400': { description: 'Validation error' }, '403': { description: 'Forbidden' } }
      }
    },
    '/jobs/{id}': {
      get: {
        tags: ['Jobs'], summary: 'Get a single job', security: [],
        parameters: [{ in: 'path', name: 'id', required: true, schema: { type: 'integer' } }],
        responses: { '200': { description: 'Job details' }, '404': { description: 'Not found' } }
      },
      put: {
        tags: ['Jobs'], summary: 'Update a job (admin: canManageJobs)',
        parameters: [{ in: 'path', name: 'id', required: true, schema: { type: 'integer' } }],
        requestBody: { content: { 'application/json': { schema: { '$ref': '#/components/schemas/Job' } } } },
        responses: { '200': { description: 'Updated job' }, '404': { description: 'Not found' } }
      },
      delete: {
        tags: ['Jobs'], summary: 'Delete a job (admin: canManageJobs)',
        parameters: [{ in: 'path', name: 'id', required: true, schema: { type: 'integer' } }],
        responses: { '200': { description: 'Deleted' }, '404': { description: 'Not found' } }
      }
    },
    // ── Applications ─────────────────────────────────────────────────────────
    '/applications': {
      get: {
        tags: ['Applications'], summary: 'List applications (own for candidates; all+filter for admins)',
        parameters: [
          { in: 'query', name: 'jobId', schema: { type: 'integer' } },
          { in: 'query', name: 'status', schema: { type: 'string' } },
          { in: 'query', name: 'fromDate', schema: { type: 'string', format: 'date' } },
          { in: 'query', name: 'toDate', schema: { type: 'string', format: 'date' } },
          { in: 'query', name: 'email', schema: { type: 'string' } }
        ],
        responses: { '200': { description: 'Applications list' } }
      },
      post: {
        tags: ['Applications'], summary: 'Submit an application',
        requestBody: { required: true, content: { 'application/json': { schema: {
          type: 'object', required: ['jobId'],
          properties: {
            jobId: { type: 'integer' }, completion: { type: 'integer', minimum: 0, maximum: 100 },
            cgpa: { type: 'number', minimum: 0, maximum: 5 },
            university: { type: 'string' }, screeningAnswers: { type: 'object' }
          }
        }}}},
        responses: { '201': { description: 'Application submitted' }, '409': { description: 'Already applied' } }
      }
    },
    '/applications/export': {
      get: {
        tags: ['Applications'], summary: 'Export applications as CSV (admin: canViewApplications)',
        parameters: [
          { in: 'query', name: 'jobId', schema: { type: 'integer' } },
          { in: 'query', name: 'status', schema: { type: 'string' } }
        ],
        responses: { '200': { description: 'CSV file', content: { 'text/csv': {} } } }
      }
    },
    '/applications/bulk-status': {
      put: {
        tags: ['Applications'], summary: 'Bulk update application statuses (admin: canShortlist)',
        requestBody: { required: true, content: { 'application/json': { schema: {
          type: 'object', required: ['updates'],
          properties: { updates: { type: 'array', items: {
            type: 'object', properties: { id: { type: 'integer' }, status: { type: 'string' } }
          }}}
        }}}},
        responses: { '200': { description: 'Updated count' } }
      }
    },
    '/applications/{id}/status': {
      put: {
        tags: ['Applications'], summary: 'Update single application status (admin: canShortlist)',
        parameters: [{ in: 'path', name: 'id', required: true, schema: { type: 'integer' } }],
        requestBody: { required: true, content: { 'application/json': { schema: {
          type: 'object', required: ['status'],
          properties: {
            status: { type: 'string', enum: ['Pending','Shortlisted','Interview','Offered','Declined'] },
            notifyEmail: { type: 'string', format: 'email' },
            notifyMessage: { type: 'string', maxLength: 2000 }
          }
        }}}},
        responses: { '200': { description: 'Updated application' } }
      }
    },
    '/applications/{id}': {
      delete: {
        tags: ['Applications'], summary: 'Withdraw own application (candidate)',
        parameters: [{ in: 'path', name: 'id', required: true, schema: { type: 'integer' } }],
        responses: { '200': { description: 'Withdrawn' } }
      }
    },
    // ── CV ────────────────────────────────────────────────────────────────────
    '/cv': {
      get: { tags: ['CV'], summary: 'Get own CV', responses: { '200': { description: 'CV profile' } } },
      put: { tags: ['CV'], summary: 'Save/update own CV', responses: { '200': { description: 'Updated CV' } } }
    },
    '/cv/upload': {
      post: {
        tags: ['CV'], summary: 'Upload a file to Cloudinary (photo or document)',
        requestBody: { required: true, content: { 'multipart/form-data': { schema: {
          type: 'object', required: ['file'],
          properties: { file: { type: 'string', format: 'binary' }, type: { type: 'string', enum: ['photo','document'] } }
        }}}},
        responses: { '201': { description: 'Upload result with url and publicId' } }
      }
    },
    '/cv/by-email/{email}': {
      get: {
        tags: ['CV'], summary: 'Get CV by email (admin: canViewApplications)',
        parameters: [{ in: 'path', name: 'email', required: true, schema: { type: 'string' } }],
        responses: { '200': { description: 'CV profile or null' } }
      }
    },
    // ── Other resource groups (abbreviated) ──────────────────────────────────
    '/criteria/{jobId}': {
      get: { tags: ['Criteria'], summary: 'Get screening criteria for a job', parameters: [{ in: 'path', name: 'jobId', required: true, schema: { type: 'integer' } }], responses: { '200': { description: 'Criteria' } } },
      put: { tags: ['Criteria'], summary: 'Save criteria (admin: canManageCriteria)', parameters: [{ in: 'path', name: 'jobId', required: true, schema: { type: 'integer' } }], responses: { '200': { description: 'Saved criteria' } } }
    },
    '/settings': {
      get: { tags: ['Settings'], summary: 'Get portal settings', responses: { '200': { description: 'Settings object' } } },
      put: { tags: ['Settings'], summary: 'Update portal settings (admin: canManageSettings)', responses: { '200': { description: 'Updated settings' } } }
    },
    '/permissions/{adminId}': {
      get: { tags: ['Permissions'], summary: 'Get permission overrides for an admin', parameters: [{ in: 'path', name: 'adminId', required: true, schema: { type: 'integer' } }], responses: { '200': { description: 'Permissions' } } },
      put: { tags: ['Permissions'], summary: 'Set permission overrides (admin: canGrantPermissions)', parameters: [{ in: 'path', name: 'adminId', required: true, schema: { type: 'integer' } }], responses: { '200': { description: 'Updated permissions' } } }
    },
    '/notifications': {
      get: { tags: ['Notifications'], summary: 'Get own notifications', responses: { '200': { description: 'Notifications list' } } }
    },
    '/notifications/{id}/read': {
      put: { tags: ['Notifications'], summary: 'Mark notification as read', parameters: [{ in: 'path', name: 'id', required: true, schema: { type: 'integer' } }], responses: { '200': { description: 'Marked read' } } }
    },
    '/emails': {
      get: { tags: ['Emails'], summary: 'Get sent email log (admin: canViewApplications)', responses: { '200': { description: 'Emails list' } } },
      post: { tags: ['Emails'], summary: 'Send and log an email (admin: canSendNotifications)', responses: { '201': { description: 'Sent and logged' } } },
      delete: { tags: ['Emails'], summary: 'Clear email log (super admin only)', responses: { '200': { description: 'Cleared' } } }
    },
    '/emails/bulk': {
      post: { tags: ['Emails'], summary: 'Send bulk emails (admin: canSendNotifications)', responses: { '201': { description: 'Sent and logged' } } }
    },
    '/audit': {
      get: { tags: ['Audit'], summary: 'Get audit log (admin: canViewAudit)', responses: { '200': { description: 'Audit entries' } } }
    },
    '/analytics': {
      get: { tags: ['Analytics'], summary: 'Get analytics summary (admin: canViewAudit)', responses: { '200': { description: 'Analytics data' } } }
    },
    '/analytics/event': {
      post: { tags: ['Analytics'], summary: 'Track an analytics event (public)', security: [], responses: { '201': { description: 'Recorded' } } }
    },
    '/staff': {
      get: { tags: ['Staff'], summary: 'List staff (admin: canViewStaff)', responses: { '200': { description: 'Staff list' } } },
      post: { tags: ['Staff'], summary: 'Add staff record (super admin)', responses: { '201': { description: 'Created' } } }
    },
    '/staff/{id}': {
      put: { tags: ['Staff'], summary: 'Update staff record (super admin)', parameters: [{ in: 'path', name: 'id', required: true, schema: { type: 'integer' } }], responses: { '200': { description: 'Updated' } } },
      delete: { tags: ['Staff'], summary: 'Delete staff record (super admin)', parameters: [{ in: 'path', name: 'id', required: true, schema: { type: 'integer' } }], responses: { '200': { description: 'Deleted' } } }
    }
  }
};

const options = {
  customCss: '.swagger-ui .topbar { background: #1a3a6e; }',
  customSiteTitle: 'CAA Recruitment API Docs'
};

module.exports = { serve: swaggerUi.serve, setup: swaggerUi.setup(spec, options), spec };
