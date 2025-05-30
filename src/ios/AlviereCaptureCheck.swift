//
//  AlviereCaptureCheck.swift
//  HelloCordova
//
//  Created by Luis Bouça on 31/05/2022.
//  Refactored by André Grillo on 23/01/2023
//  Refactored by André Grillo on 24/04/2025

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
    
    @objc(setEnvironment:)
    func setEnvironment(command: CDVInvokedUrlCommand) {
        guard let environmentString = command.arguments.first as? String else {
            self.commandDelegate.send(
                CDVPluginResult(status: .error, messageAs: "Missing environment argument"),
                callbackId: command.callbackId
            )
            return
        }

        Task {
            if environmentString.lowercased() == "sandbox" {
                let result = await AlCoreSDK.shared.setEnvironment(.sandbox)
                self.commandDelegate.send(
                    CDVPluginResult(status: .ok),
                    callbackId: command.callbackId
                )
                print("result sandbox: \(result)")
            } else {
                let result = await AlCoreSDK.shared.setEnvironment(.production)
                self.commandDelegate.send(
                    CDVPluginResult(status: .ok),
                    callbackId: command.callbackId
                )
                print("result production: \(result)")
            }
        }
    }
    
    override func pluginInitialize(){
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
              let docTypes = arguments["docTypes"] as? [String],
              let token = arguments["token"] as? String,
              let cameraConfigRaw = docTypes.first else {
            sendPluginResult(status: .error, message: "Missing or invalid arguments", callbackType: .dossier)
            return
        }

        print("🪪 Received accountUUID: \(accountUUID)")
        print("🔑 Received token: \(token)")
        
        AlCoreSDK.shared.setAuthToken(token)

        let cameraPermission = AVCaptureDevice.authorizationStatus(for: .video)
        if cameraPermission != .authorized {
            AVCaptureDevice.requestAccess(for: .video) { granted in
                if !granted {
                    self.sendPluginResult(status: .error, message: "Camera permission denied", callbackType: .dossier)
                }
            }
            return
        }

        Task { @MainActor in
            do {
                print("📸 Getting camera token...")
                let cameraToken = try await AlCoreSDK.shared.getCameraToken(accountUUID: accountUUID)
                print("✅ Got camera token:", cameraToken)

                let config: AccountsSDK.ALCameraConfiguration
                switch cameraConfigRaw {
                case "DRIVER_LICENSE_BACK":
                    config = .driverLicenseBack
                case "DRIVER_LICENSE_FRONT":
                    config = .driverLicenseFront
                case "ID_DOCUMENT_BACK":
                    config = .idDocumentBack
                case "ID_DOCUMENT_FRONT":
                    config = .idDocumentFront
                case "INE_BACK":
                    config = .ineBack
                case "INE_FRONT":
                    config = .ineFront
                case "MC_DOCUMENT_BACK":
                    config = .matriculaConsularBack
                case "MC_DOCUMENT_FRONT":
                    config = .matriculaConsularFront
                case "PASSPORT":
                    config = .passport
                case "PROOF_OF_ADDRESS":
                    config = .proofOfAddress
                case "PROOF_OF_FUNDS":
                    config = .proofOfFunds
                case "SELFIE":
                    config = .selfie
                default:
                    sendPluginResult(status: .error, message: "Invalid document type: \(cameraConfigRaw)", callbackType: .dossier)
                    return
                }

                let captureView = AlAccounts.userInterface.createCaptureDocumentView(
                    cameraToken: cameraToken,
                    cameraConfig: config,
                    overlay: nil
                ) { result in
                    switch result {
                    case .success(let captureData):
                        DispatchQueue.main.async {
                            self.viewController.dismiss(animated: true) {
                                let resultDict: [String: Any] = [
                                    "image": captureData.image
                                ]
                                if let jsonData = try? JSONSerialization.data(withJSONObject: resultDict, options: []),
                                   let jsonString = String(data: jsonData, encoding: .utf8) {
                                    self.sendPluginResult(status: .ok, message: jsonString, callbackType: .dossier)
                                } else {
                                    self.sendPluginResult(status: .error, message: "Failed to serialize capture data", callbackType: .dossier)
                                }
                            }
                        }

                    case .failure(let error):
                        DispatchQueue.main.async {
                            self.viewController.dismiss(animated: true) {
                                 self.sendPluginResult(status: .error, message: "Error: \(error.localizedDescription)", callbackType: .dossier)       
                            }
                        }

                    @unknown default:
                        DispatchQueue.main.async {
                            self.viewController.dismiss(animated: true) {
                                 self.sendPluginResult(status: .error, message: "Unknown result state", callbackType: .dossier)                         
                            }
                        }
                    }
                }

                let hostingController = UIHostingController(rootView: captureView)
                self.viewController.present(hostingController, animated: true, completion: nil)

            } catch {
                print("❌ Failed to get camera token: \(error.localizedDescription)")
                self.sendPluginResult(status: .error, message: "Exception: \(error.localizedDescription)", callbackType: .dossier)
            }
        }
    }
    
    @objc(captureCheck:)
    func captureCheck(command: CDVInvokedUrlCommand){
        guard let accountUUID = command.arguments[0] as? String,
        let token = command.arguments[1] as? String else {
            sendPluginResult(status: .error, message: "Invalid accountUUID or token", callbackType: .check)
            return
        }
        print("🪪 Received accountUUID: \(accountUUID)")
        print("🔑 Received token: \(token)")
        
        AlCoreSDK.shared.setAuthToken(token)
        
        let cameraPermission = AVCaptureDevice.authorizationStatus(for: .video)
        if cameraPermission != .authorized {
            AVCaptureDevice.requestAccess(for: .video) { granted in
                if !granted {
                    self.sendPluginResult(status: .error, message: "Camera permission denied", callbackType: .check)
                } //else {
//                    DispatchQueue.main.async {
//                        self.captureCheck(command: command) // Retry
//                    }
//                }
            }
            return
        }
        
        Task { @MainActor in
            do {
                print("📸 Getting camera token...")
                let cameraToken = try await AlCoreSDK.shared.getCameraToken(accountUUID: accountUUID)
                print("✅ Got camera token:", cameraToken)
                
                var config = ALCameraConfiguration.checkFront
                
                let captureView = AlPayments.userInterface.createCaptureCheckView(
                    cameraToken: cameraToken,
                    cameraConfig: config,
                    overlay: nil
                ) { result in
                    switch result {
                    case .success(let checkData):
                        DispatchQueue.main.async {
                            self.viewController.dismiss(animated: true) {
                                let resultDict: [String: Any] = [
                                    "image": checkData.image
                                ]
                                if let jsonData = try? JSONSerialization.data(withJSONObject: resultDict, options: []),
                                   let jsonString = String(data: jsonData, encoding: .utf8) {
                                    self.sendPluginResult(status: .ok, message: jsonString, callbackType: .check)
                                } else {
                                    self.sendPluginResult(status: .error, message: "Failed to serialize check data", callbackType: .check)
                                }
                            }
                        }
                        
                    case .failure(let error):
                        DispatchQueue.main.async {
                            self.viewController.dismiss(animated: true) {
                                self.sendPluginResult(status: .error, message: "Error: \(error.localizedDescription)", callbackType: .check)
                            }
                        }
                        
                    @unknown default:
                        DispatchQueue.main.async {
                            self.viewController.dismiss(animated: true) {
                                self.sendPluginResult(status: .error, message: "Unknown result state", callbackType: .check)
                            }
                        }
                    }
                }
                
                let hostingController = UIHostingController(rootView: captureView)
                self.viewController.present(hostingController, animated: true, completion: nil)
                
            } catch {
                print("❌ Failed to get camera token: \(error.localizedDescription)")
                self.sendPluginResult(status: .error, message: "Exception: \(error.localizedDescription)", callbackType: .check)
            }
        }
    }
    
    @objc(checkPermission:)
    func checkPermission(command: CDVInvokedUrlCommand) {
        if AVCaptureDevice.authorizationStatus(for: AVMediaType.video) ==  AVAuthorizationStatus.authorized {
            // Already Authorized
            commandDelegate.send(CDVPluginResult(status: .ok, messageAs: true), callbackId: command.callbackId)
        } else {
            commandDelegate.send(CDVPluginResult(status: .ok, messageAs: false), callbackId: command.callbackId)
        }
    }
    
    @objc(requestPermission:)
    func requestPermission(command: CDVInvokedUrlCommand) {
        if AVCaptureDevice.authorizationStatus(for: .video) == .authorized {
            // Already Authorized
            commandDelegate.send(CDVPluginResult(status: .ok, messageAs: true), callbackId: command.callbackId)
        } else {
            AVCaptureDevice.requestAccess(for: .video) { granted in
                if granted {
                    self.commandDelegate.send(CDVPluginResult(status: .ok, messageAs: true), callbackId: command.callbackId)
                } else {
                    self.commandDelegate.send(CDVPluginResult(status: .ok, messageAs: false), callbackId: command.callbackId)
                }
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
