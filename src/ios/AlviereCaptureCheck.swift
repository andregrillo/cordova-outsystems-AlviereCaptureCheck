//
//  AlviereCaptureCheck.swift
//  HelloCordova
//
//  Created by Luis Bouça on 31/05/2022.
//  Refactored by André Grillo on 23/01/2023
//  Refactored by André Grillo on 22/04/2025

import Foundation
import AlCore
import PaymentsSDK
import AccountsSDK
import AVFoundation
import UIKit
import SwiftUI

@objc(AlviereCaptureCheck)
class AlviereCaptureCheck: CDVPlugin {
    var pluginCallback = PluginCallback()
    
    override func pluginInitialize() {
        pluginCallback = PluginCallback()
        print("⭐️ \(pluginCallback)")
    }
        
    @objc(hideNavigationBar:)
    func hideNavigationBar(command: CDVInvokedUrlCommand){
        if (self.viewController.navigationController != nil){
            self.viewController.navigationController!.isNavigationBarHidden = true
        }
        self.commandDelegate.send(CDVPluginResult(status:.ok), callbackId: command.callbackId);
    }

    @objc(setCheckCallbacks:)
    func setCheckCallbacks(command: CDVInvokedUrlCommand) {
        pluginCallback.resetCallbacks()
        pluginCallback.checkCallbackID = command.callbackId
    }

    @objc(setDossierCallbacks:)
    func setDossierCallbacks(command: CDVInvokedUrlCommand) {
        pluginCallback.resetCallbacks()
        pluginCallback.dossierCallbackID = command.callbackId
    }

    @objc(captureDossier:)
    func captureDossier(command: CDVInvokedUrlCommand) {
        guard let arguments = command.arguments.first as? [String: Any],
              let accountUUID = arguments["accountUUID"] as? String,
              let docTypes = arguments["docTypes"] as? [String] else {
            sendPluginResult(status: .error, message: "Missing or invalid arguments", callbackType: .dossier)
            return
        }

        let cameraPermission = AVCaptureDevice.authorizationStatus(for: .video)
        if cameraPermission != .authorized {
            AVCaptureDevice.requestAccess(for: .video) { granted in
                if granted {
                    DispatchQueue.main.async {
                        self.captureDossier(command: command)
                    }
                } else {
                    self.sendPluginResult(status: .error, message: "Camera permission denied", callbackType: .dossier)
                }
            }
            return
        }

        Task {
            do {
                let cameraToken = try await AlCoreSDK.shared.getCameraToken(accountUUID: accountUUID)

                let documentTypes = docTypes.compactMap { DocumentType(rawValue: $0) }

                let uploadRequest: AccountDossierUploadRequest = .create(
                    accountUuid: accountUUID,
                    isPrimary: true,
                    externalId: UUID().uuidString,
                    realTimeVerification: false
                )

                let uploadView = await AlAccounts.userInterface.createDossierUploadView(
                    cameraToken: cameraToken,
                    documentTypes: documentTypes,
                    data: uploadRequest,
                    overlay: nil,
                    loading: nil,
                    failure: nil
                ) { result in
                    switch result {
                    case .success(let dossier):
                        if let jsonData = try? JSONEncoder().encode(dossier),
                           let jsonString = String(data: jsonData, encoding: .utf8) {
                            self.sendPluginResult(status: .ok, message: jsonString, callbackType: .dossier)
                        } else {
                            self.sendPluginResult(status: .error, message: "Failed to encode result", callbackType: .dossier)
                        }
                    case .failure(let error):
                        self.sendPluginResult(status: .error, message: error.localizedDescription, callbackType: .dossier)
                    }
                }

                let hostingController = UIHostingController(rootView: uploadView)
                self.viewController.present(hostingController, animated: true, completion: nil)

            } catch {
                sendPluginResult(status: .error, message: "Exception: \(error.localizedDescription)", callbackType: .dossier)
            }
        }
    }
    
    @objc(captureCheck:)
    func captureCheck(command: CDVInvokedUrlCommand) {
        guard let accountUUID = command.arguments.first as? String else {
            sendPluginResult(status: .error, message: "Missing or invalid accountUUID", callbackType: .check)
            return
        }

        let cameraPermission = AVCaptureDevice.authorizationStatus(for: .video)
        if cameraPermission != .authorized {
            AVCaptureDevice.requestAccess(for: .video) { granted in
                if !granted {
                    self.sendPluginResult(status: .error, message: "Camera permission denied", callbackType: .check)
                } else {
                    DispatchQueue.main.async {
                        self.captureCheck(command: command) // Retry
                    }
                }
            }
            return
        }

        Task {
            do {
                let cameraToken = try await AlCoreSDK.shared.getCameraToken(accountUUID: accountUUID)

                var config = ALCameraConfiguration.checkFront
                // Optionally customize config if needed

                let captureView = await AlPayments.userInterface.createCaptureDocumentView(
                    cameraToken: cameraToken,
                    cameraConfig: config,
                    overlay: nil
                ) { result in
                    switch result {
                    case .success(let checkData):
                        if let jsonData = try? JSONEncoder().encode(checkData),
                           let jsonString = String(data: jsonData, encoding: .utf8) {
                            self.sendPluginResult(status: .ok, message: jsonString, callbackType: .check)
                        } else {
                            self.sendPluginResult(status: .error, message: "Failed to encode result", callbackType: .check)
                        }
                    case .failure(let error):
                        self.sendPluginResult(status: .error, message: "Error: \(error.localizedDescription)", callbackType: .check)
                    }
                }

                let hostingController = await UIHostingController(rootView: captureView)
                await self.viewController.present(hostingController, animated: true, completion: nil)

            } catch {
                sendPluginResult(status: .error, message: "Exception: \(error.localizedDescription)", callbackType: .check)
            }
        }
    }
        
    func sendPluginResult(status: CDVCommandStatus, message: String, callbackType: CallbackType, callbackID: String = "" ) {
        var pluginResult = CDVPluginResult(status: status, messageAs: message)
        if callbackType == .dossier {
            if (pluginCallback.dossierCallbackID) != nil {
                self.commandDelegate!.send(pluginResult, callbackId: pluginCallback.dossierCallbackID)
            }
        } else if callbackType == .check {
            if (pluginCallback.checkCallbackID) != nil {
                self.commandDelegate!.send(pluginResult, callbackId: pluginCallback.checkCallbackID)
            }
        } else if (callbackType == .other) {
            if status == CDVCommandStatus_OK {
                pluginResult = CDVPluginResult(status: status, messageAs: true)
                self.commandDelegate!.send(pluginResult, callbackId: callbackID)
            }
            else if status == CDVCommandStatus_ERROR {
                pluginResult = CDVPluginResult(status: status, messageAs: false)
                self.commandDelegate!.send(pluginResult, callbackId: callbackID)
            }
        }
    }
}

class PluginCallback {
    var dossierCallbackID: String?
    var checkCallbackID: String?
    
    func resetCallbacks(){
        self.dossierCallbackID = nil
        self.checkCallbackID = nil
    }
}

enum CallbackType {
    case check
    case dossier
    case other
}

