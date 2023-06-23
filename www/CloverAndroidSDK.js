var exec = require('cordova/exec');

exports.isClover = function(success, error) {
    exec(success, error, "CloverAndroidSDK", "isClover", []);
};

exports.startScan = function(success, error) {
    exec(success, error, "CloverAndroidSDK", "startScan", []);
};

exports.printTextReceipt = function(receipt, success, error) {
    exec(success, error, "CloverAndroidSDK", "printTextReceipt", [receipt]);
};

exports.processBarcode = function(barcodeResult) {
    return barcodeResult;
};

// exports.coolMethod = function (arg0, success, error) {
//     exec(success, error, 'CloverAndroidSDK', 'coolMethod', [arg0]);
// };
