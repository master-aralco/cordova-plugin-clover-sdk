cordova.define("cordova-plugin-clover-sdk.CloverAndroidSDK", function(require, exports, module) {
var exec = require('cordova/exec');

/**
 * Returns a new UID for use with payments
 *
 * @param success Function to call on success. Passes in one string param to returned function, a new UID
 * @param error Function to call on error.
 */
exports.getNewUID = function(success, error) {
    exec(success, error, "CloverAndroidSDK", "getNewUID", []);
};

/**
 * Determin if the current device is a clover or clover compatible device
 *
 * @param success Function to call on success. Passes in one bool param to returned function, true if on clover devices, otherwise false.
 * @param error Function to call on error. Should never be called in the current implementation.
 */
exports.isClover = function(success, error) {
    exec(success, error, "CloverAndroidSDK", "isClover", []);
};

/**
 * Starts a scan using the clover barcode camera
 *
 * @param success Function to call on success. Passes in one string on successful scan. Method not called on cancel.
 * @param error Function to call on error. Should never be called in the current implementation.
 */
exports.startScan = function(success, error) {
    exec(success, error, "CloverAndroidSDK", "startScan", []);
};

/**
 * Prints a text only receipt.
 *
 * @param receipt The string text to print. Use \r\n for new lines.
 * @param success Function to call on success. Passes in one bool param to returned function, true if the printing succeded, otherwise false.
 * @param error Function to call on error. Passes in an error object to returned function.
 */
exports.printTextReceipt = function(receipt, success, error) {
    exec(success, error, "CloverAndroidSDK", "printTextReceipt", [receipt]);
};

/**
 * Takes a payment.
 *
 * @param {*} amount The whole number of cents to charge, e.g. 1000 for $10.00
 * @param {*} orderID The order number to apply to the transaction. Must be unique and cannot be re-used. Leave falsy for auto generated.
 * @param {*} success Function to call on success. Passes an object describing the transaction to returned function.
 * @param {*} error Function to call on error. Passes in an error object to returned function.
 */
exports.takePayment = function(amount, uid, success, error) {
    exec(success, error, "CloverAndroidSDK", "takePayment", [amount, uid]);
}

exports.refund = function(amount, uid, success, error) {
    exec(success, error, "CloverAndroidSDK", "refund", [amount, uid]);
}

});
