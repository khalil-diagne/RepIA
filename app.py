from flask import Flask, send_from_directory


app = Flask(
    __name__,
    static_folder="static",
    static_url_path="/static",
)


@app.route("/")
def index():
    return send_from_directory(".", "index.html")


@app.route("/pose_landmarker_lite.task")
def modele():
    return send_from_directory(".", "pose_landmarker_lite.task")


if __name__ == "__main__":
    print("=" * 52)
    print("RepIA (preview locale) : http://localhost:5000")
    print("Pour deployer : https://vercel.com  ou  npx vercel --prod")
    print("=" * 52)
    app.run(host="0.0.0.0", port=5000, threaded=True)