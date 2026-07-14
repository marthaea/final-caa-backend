// utils/asyncHandler.js
// Wraps async route handlers so thrown errors are forwarded to Express's
// error-handling middleware instead of crashing the process.

function asyncHandler(fn) {
  return function (req, res, next) {
    Promise.resolve(fn(req, res, next)).catch(next);
  };
}

module.exports = asyncHandler;
