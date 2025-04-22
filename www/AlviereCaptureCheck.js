var exec = require('cordova/exec');

exports.captureCheck = function (accountUUID, success, error) {
    exec(success, error, 'AlviereCaptureCheck', 'captureCheck', [accountUUID]);
};

exports.captureDossier = function (accountUUID, docTypes, success, error) {
    const payload = {
        accountUUID: accountUUID,
        docTypes: docTypes
    };
    exec(success, error, 'AlviereCaptureCheck', 'captureDossier', [payload]);
};

exports.requestPermission = function (success, error) {
    exec(success, error, 'AlviereCaptureCheck', 'requestPermission', []);
};
exports.checkPermission = function (success, error) {
    exec(success, error, 'AlviereCaptureCheck', 'checkPermission', []);
};

exports.setCheckCallbacks = function (success, error) {
    exec(success, error, 'AlviereCaptureCheck', 'setCheckCallbacks', []);
};

exports.setDossierCallbacks = function (success, error) {
    exec(success, error, 'AlviereCaptureCheck', 'setDossierCallbacks', []);
}

exports.hideNavigationBar = function (success, error) {
    exec(success, error, 'AlviereCaptureCheck', 'hideNavigationBar', []);
}
