/* eslint-disable linebreak-style */
/* eslint-disable require-jsdoc */
/* eslint-disable max-len */
const functions = require("firebase-functions");
const admin = require("firebase-admin");
const {google} = require("googleapis");
const fs = require("fs");
const cors = require("cors")({origin: true});

admin.initializeApp();


async function validatePurchase(packageName, productId, purchaseToken) {
  try {
    console.log("Validating purchase:", {packageName, productId, purchaseToken});
    const keyContent = fs.readFileSync("./keys/samay-amiga-virtual-514f2-29bf15c21509.json");
    const key = JSON.parse(keyContent);

    const jwtClient = new google.auth.JWT(
        key.client_email,
        null,
        key.private_key,
        ["https://www.googleapis.com/auth/androidpublisher"],
        null,
    );

    await jwtClient.authorize();

    const androidpublisher = google.androidpublisher({
      version: "v3",
      auth: jwtClient,
    });

    const response = await androidpublisher.purchases.products.get({
      packageName: packageName,
      productId: productId,
      token: purchaseToken,
    });
    if (response && response.data && response.data.purchaseState === 0) {
      console.log("Purchase validated:", response.data);
      return response.data;
    } else {
      console.log("Purchase not validated");
      return null;
    }
  } catch (error) {
    console.error("Error validating purchase: ", error);
    return null;
  }
}

exports.handleDeveloperNotification = functions.https.onRequest((req, res) => {
  cors(req, res, async () => {
    const authHeader = req.header("Authorization");
    console.log("req: ", req);

    if (!authHeader) {
      console.error("Missing Authorization header");
      res.status(401).send("Unauthorized");
      return;
    }

    const idToken = authHeader.split("Bearer ")[1];
    console.log("idToken: ", idToken);


    try {
      const decodedToken = await admin.auth().verifyIdToken(idToken);
      const userId = decodedToken.uid;

      const packageName = req.body.packageName;
      const productId = req.body.productId;
      const purchaseToken = req.body.purchaseToken;

      const isValidPurchase = await validatePurchase(packageName, productId, purchaseToken);
      if (isValidPurchase) {
        // Update Firestore document to add 50 credits to the user's account
        const userDocRef = admin.firestore().collection("users").doc(userId);
        await userDocRef.update({
          credits: admin.firestore.FieldValue.increment(50),
        });
      }

      // Send a success response
      res.status(200).send("Purchase processed successfully");
    } catch (error) {
      console.error("Error verifying ID token:", error);
      res.status(401).send("Unauthorized");
    }
  });
});
