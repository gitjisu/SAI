<template>
    <div class="container mt-5" id="body">
      <div><canvas id="canvas" v-show="false"></canvas></div>
      <div class="modal fade" id="exampleModalToggle" aria-hidden="true"
      aria-labelledby="exampleModalToggleLabel" tabindex="-1">
        <div class="modal-dialog modal-dialog-centered">
          <div class="modal-content">
            <div class="modal-header">
              <h5 class="modal-title" id="exampleModalToggleLabel">면접을 끝내시겠습니까?</h5>
              <button type="button" class="btn-close"
              data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body">
              <div class="col-12">
                <div class="form-check d-flex" style="padding-left: 0">
                  <router-link to="/main">
                    <button type="button" @click="leaveSession()" id="modal-btn2"
                    data-bs-dismiss="modal" aria-label="Close">예</button>
                  </router-link>
                  <button type="button" id="modal-btn2"
                   data-bs-dismiss="modal" aria-label="Close">아니오</button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div id="session" v-if="session">
        <div id="main-video">
          <div class="d-flex">
            <div id="video-box">
              <user-video :stream-manager="mainStreamManager"
              @emotionRatioCount="emotionRatioCount"/>
            </div>
            <div id="video-container" class="col-md-6">
              <user-video v-for="sub in subscribers"
              :key="sub.stream.connection.connectionId"
              :stream-manager="sub" @click="updateMainVideoStreamManager(sub)"/>
            </div>
          </div>
          <div class="d-flex justify-content-end" style="margin-right:20px;">
            <input class="btn btn-light" type="button" v-if="!isRecording &&
            this.currentUser.memberStatus == 'CONSULTANT'"
              id="buttonLeaveSession" @click="startRecoding" value="시작"
              :style="[isRecording == true ?
              {background:'#e52b50', color:'#ffffff'} : {background: '#f8f9fa'}]">
              <input class="btn btn-light" type="button" v-if="isRecording &&
              this.currentUser.memberStatus == 'CONSULTANT'"
              id="buttonLeaveSession" @click="stopRecoding()" value="답변 완료">
            <div class="d-flex justify-content-end">
              <button class="btn" data-bs-toggle="modal" @keydown="s"
              data-bs-target="#exampleModalToggle" id="modal-btn">면접 종료
              </button>
            </div>
          </div>
        </div>
    </div>
  </div>
</template>

<script>
import axios from 'axios';
import drf from '@/api/api';
import { OpenVidu } from 'openvidu-browser';
import { computed, onBeforeMount } from 'vue';
import { useStore } from 'vuex';
import * as tmPose from '@teachablemachine/pose';
// eslint-disable-next-line
import * as tf from '@tensorflow/tfjs';
import UserVideo from './components/UserVideo.vue';

axios.defaults.headers.post['Content-Type'] = 'application/json';

const OPENVIDU_SERVER_URL = 'https://i7c206.p.ssafy.io:8083';
const OPENVIDU_SERVER_SECRET = 'MY_SECRET';

export default {
  // name: '',
  components: {
    UserVideo,
  },
  data() {
    return {
      isAnimationStart: false,
      myRecodingId: undefined,
      OV: undefined,
      session: undefined,
      mainStreamManager: undefined,
      publisher: undefined,
      subscribers: [],
      mySessionId: `${this.upcomingSchedules[0].id}`,
      myUserName: `Participant${Math.floor(Math.random() * 100)}`,
      isStart: false,
      question: '',
      questions: [],
      questionsTTS: [],
      isFinished: false,
      myConfirms: false,
      ctConfirms: false,
      savedUrls: [],
      isRecording: false,
      consultantsPK: null,
      savedQ: null,
      wrongPostureCount: [],
      preWrongCount: 0,
      emotionRatio: [],
      happy: 0,
      emotionCount: 1,
    };
  },
  setup() {
    const store = useStore();
    const selectedQuestionList = computed(() => store.getters.selectedQuestionList);
    const selectedQuestionListTTS = computed(() => store.getters.selectedQuestionListTTS);
    const currentUser = computed(() => store.getters.currentUser);
    const consultants = computed(() => store.getters.myConsultants);
    const upcomingSchedules = computed(() => store.getters.upcomingSchedules);
    const fetchUpcomingSchedules = () => {
      store.dispatch('fetchUpcomingSchedules');
    };
    onBeforeMount(() => {
      fetchUpcomingSchedules();
    });
    const TMURL = 'https://teachablemachine.withgoogle.com/models/xOFsAlFmy/';
    let model; let webcam; let ctx; let labelContainer; let maxPredictions;

    function drawPose(pose) {
      if (webcam.canvas) {
        ctx.drawImage(webcam.canvas, 0, 0);
        if (pose) {
          const minPartConfidence = 0.5;
          tmPose.drawKeypoints(pose.keypoints, minPartConfidence, ctx);
          tmPose.drawSkeleton(pose.keypoints, minPartConfidence, ctx);
        }
      }
    }
    let progress = 327;
    let status = 'proper posture';
    let count = 0;
    function countOutput() {
      return {
        count,
      };
    }
    async function predict() {
      const { pose, posenetOutput } = await model.estimatePose(webcam.canvas);
      const prediction = await model.predict(posenetOutput);
      if (prediction[0].probability.toFixed(2) >= 0.70) {
        if (status !== 'proper posture') {
          count += 1;
          console.log(count);
          countOutput();
          progress -= 32.7;
          if (progress <= 0) {
            progress = 327 - 32.7;
          }
        }
        status = 'proper posture';
      } else if (prediction[1].probability.toFixed(2) >= 0.70) {
        status = 'wrong posture - left';
      } else if (prediction[2].probability.toFixed(2) >= 0.70) {
        status = 'wrong posture - right';
      } else if (prediction[3].probability.toFixed(2) >= 0.70) {
        status = 'wrong posture - bent';
      }

      for (let i = 0; i < maxPredictions; i += 1) {
        const classPrediction = `${prediction[i].className}: ${prediction[i].probability.toFixed(2)}`;
        labelContainer.childNodes[i].innerHTML = classPrediction;
      }

      drawPose(pose);
    }

    // eslint-disable-next-line
    async function loop(timestamp) {
      webcam.update();
      await predict();
      window.requestAnimationFrame(loop);
    }

    async function init() {
      const modelURL = `${TMURL}model.json`;
      const metadataURL = `${TMURL}metadata.json`;

      model = Object.freeze(await tmPose.load(modelURL, metadataURL));
      maxPredictions = model.getTotalClasses();

      const size = 500;
      const flip = true;
      webcam = new tmPose.Webcam(size, size, flip);
      await webcam.setup();
      await webcam.play();
      window.requestAnimationFrame(loop);

      const canvas = document.getElementById('canvas');
      canvas.width = size; canvas.height = size;
      ctx = canvas.getContext('2d');
      labelContainer = document.getElementById('label-container');
      for (let i = 0; i < maxPredictions; i += 1) {
        labelContainer.appendChild(document.createElement('div'));
      }
    }

    return {
      selectedQuestionList,
      selectedQuestionListTTS,
      currentUser,
      consultants,
      countOutput,
      init,
      upcomingSchedules,
      fetchUpcomingSchedules,
    };
  },
  created() {
    this.questions = this.selectedQuestionList;
    this.questionsTTS = this.selectedQuestionListTTS;
    this.savedQ = Object.values(this.selectedQuestionList);
    this.savedQTTS = Object.values(this.selectedQuestionListTTS);
  }, // 해당 vue 파일이 실행 되는 순간
  mounted() {
    this.joinSession();
    this.init();
    // this.mySessionId = this.currentUser.id;
  }, // 템플릿 내 HTML DOM이 화면에 로딩이 되는 순간, 마운트가 다 끝난 순간 실행
  unmounted() { }, // 컴포넌트 이동 시 unmount가 일어나면서 해당 코드 자동 실행
  methods: {
    emotionRatioCount(happyRatio) {
      this.happy += happyRatio;
      this.emotionCount += 1;
    },
    videoForm() {
      axios({
        url: drf.interview.saveVideo(this.upcomingSchedules[0].studentId),
        method: 'post',
        data: {
          scheduleId: null,
          feedbackRequest: this.ctConfirms,
          consultantId: this.consultantsPK,
          wrongPostureCount: this.wrongPostureCount,
          interviewVideoUrl: this.savedUrls,
          questions: this.savedQ,
          questionsTTS: this.savedQTTS,
          emotionRatio: this.emotionRatio,
        },
      })
        .then((res) => console.log(res));
    },
    ctSelect(event) {
      this.consultantsPK = event.target.value;
    },
    myConfirm(event) {
      if (event.target.value === 'true') {
        this.myConfirms = true;
      } else {
        this.myConfirms = false;
      }
    },
    ctConfirm(event) {
      if (event.target.value === 'true') {
        this.ctConfirms = true;
      } else {
        this.ctConfirms = false;
      }
    },
    answerCompleted() {
      this.question = '';
      if (!this.questions.length) {
        this.question = '모의면접이 완료되었습니다. 면접종료버튼을 눌러 모의면접을 종료하세요.';
        this.isFinished = true;
      }
      // 답변완료 버튼을 누르면 타임라인이 생성되고 다음질문이 3초뒤에 TTS.
    },
    startRecoding() {
      this.isRecording = true;
      this.isAnimationStart = true;
      this.question = this.questions.shift();
      this.questionTTS = this.questionsTTS.shift();
      console.log(this.questions);
      this.preWrongCount = this.countOutput().count;
      this.happy = 0;
      this.emotionCount = 0;

      axios
        .post(`${OPENVIDU_SERVER_URL}/openvidu/api/recordings/start`, JSON.stringify({
          session: this.mySessionId,
        }), {
          auth: {
            username: 'OPENVIDUAPP',
            password: OPENVIDU_SERVER_SECRET,
          },
        })
        .then((res) => {
          this.myRecodingId = res.data.id;
        });
    },
    stopRecoding() {
      const tmp = this.countOutput().count;
      this.isRecording = false;
      this.wrongPostureCount.push(tmp - this.preWrongCount);
      if (this.happy / this.emotionCount) {
        this.emotionRatio.push(this.happy / this.emotionCount);
      } else {
        this.emotionRatio.push(0);
      }
      console.log(this.emotionRatio);
      axios
        .post(`${OPENVIDU_SERVER_URL}/openvidu/api/recordings/stop/${this.myRecodingId}`, JSON.stringify({
          recoding: this.myRecodingId,
        }), {
          auth: {
            username: 'OPENVIDUAPP',
            password: OPENVIDU_SERVER_SECRET,
          },
        })
        .then((res) => {
          this.savedUrls.push(res.data.url);
          console.log(this.savedUrls);
        });
    },
    joinSession() {
      // --- Get an OpenVidu object ---
      this.OV = new OpenVidu();

      // --- Init a session ---
      this.session = this.OV.initSession();

      // --- Specify the actions when events take place in the session ---

      // On every new Stream received...
      this.session.on('streamCreated', ({ stream }) => {
        const subscriber = this.session.subscribe(stream);
        this.subscribers.push(subscriber);
      });

      // On every Stream destroyed...
      this.session.on('streamDestroyed', ({ stream }) => {
        const index = this.subscribers.indexOf(stream.streamManager, 0);
        if (index >= 0) {
          this.subscribers.splice(index, 1);
        }
      });

      // On every asynchronous exception...
      this.session.on('exception', ({ exception }) => {
        console.warn(exception);
      });

      // --- Connect to the session with a valid user token ---

      // 'getToken' method is simulating what your server-side should do.
      // 'token' parameter should be retrieved and returned by your own backend
      this.getToken(this.mySessionId).then((token) => {
        this.session.connect(token, { clientData: this.myUserName })
          .then(() => {
            // --- Get your own camera stream with the desired properties ---

            const publisher = this.OV.initPublisher(undefined, {
              audioSource: undefined, // The source of audio. If undefined default microphone
              videoSource: undefined, // The source of video. If undefined default webcam
              publishAudio: true,
              // Whether you want to start publishing with your audio unmuted or not
              publishVideo: true,
              // Whether you want to start publishing with your video enabled or not
              resolution: '640x480', // The resolution of your video
              frameRate: 30, // The frame rate of your video
              insertMode: 'APPEND', // How the video is inserted in the target element 'video-container'
              mirror: false, // Whether to mirror your local video or not
            });

            this.mainStreamManager = publisher;
            this.publisher = publisher;

            // --- Publish your stream ---

            this.session.publish(this.publisher);
          })
          .catch((error) => {
            console.log('There was an error connecting to the session:', error.code, error.message);
          });
      });

      window.addEventListener('beforeunload', this.leaveSession);
    },

    leaveSession() {
      // --- Leave the session by calling 'disconnect' method over the Session object ---
      if (this.session) this.session.disconnect();

      this.session = undefined;
      this.mainStreamManager = undefined;
      this.publisher = undefined;
      this.subscribers = [];
      this.OV = undefined;

      window.removeEventListener('beforeunload', this.leaveSession);
    },

    updateMainVideoStreamManager(stream) {
      if (this.mainStreamManager === stream) return;
      this.mainStreamManager = stream;
    },

    getToken(mySessionId) {
      return this.createSession(mySessionId).then((sessionId) => this.createToken(sessionId));
    },

    // See https://docs.openvidu.io/en/stable/reference-docs/REST-API/#post-session
    createSession(sessionId) {
      return new Promise((resolve, reject) => {
        const data = JSON.stringify({ customSessionId: sessionId });
        axios
          .post(`${OPENVIDU_SERVER_URL}/openvidu/api/sessions`, data, {
            headers: {
              Authorization: `Basic ${btoa(
                `OPENVIDUAPP:${OPENVIDU_SERVER_SECRET}`,
              )}`,
              'Content-type': 'application/json',
            },
          })
          .then((response) => {
            resolve(response.data.id);
          })
          .catch((error) => {
            if (error.response.status === 409) {
              resolve(sessionId);
            } else {
              console.warn(`No connection to OpenVidu Server. This may be a certificate error at ${OPENVIDU_SERVER_URL}`);
              if (window.confirm(`No connection to OpenVidu Server.This may be a certificate error at ${OPENVIDU_SERVER_URL}\n\nClick OK to navigate and accept it. If no certificate warning is shown, then check that your OpenVidu Server is up and running at "${OPENVIDU_SERVER_URL}"`)) {
                window.location.assign(`${OPENVIDU_SERVER_URL}/accept-certificate`);
              }
              reject(error.response);
            }
          });
      });
    },

    // See https://docs.openvidu.io/en/stable/reference-docs/REST-API/#post-connection
    createToken(sessionId) {
      return new Promise((resolve, reject) => {
        const data = {};
        axios
          .post(`${OPENVIDU_SERVER_URL}/openvidu/api/sessions/${sessionId}/connection`, data, {
            headers: {
              Authorization: `Basic ${btoa(
                `OPENVIDUAPP:${OPENVIDU_SERVER_SECRET}`,
              )}`,
              'Content-type': 'application/json',
            },
          })
          .then((response) => {
            resolve(response.data.token);
          })
          .catch((error) => reject(error.response));
      });
    },
  },
}; // 함수 정의해서 컴포넌트 내에서 사용 가능하게 함
</script>

<style scoped>
#ct-label {
  display: inline-block;
  width: 100%;
}
.ct-name {
  width: 100%;
}
.ct-radio {
  display: inline-block;
}
#body {
  height: 120vh;
}
#modal-btn {
  background-color: #5c6ac4;
  color: white;
}
#hi{
  position: absolute; top: 30%; width: 50%;
  color: white;
}

#video-box {
  width: 100%;
  overflow: hidden;
  margin: 0px auto;
  position: relative;
}

user-video {
  width: 100%
}

#video-text {
  display: hidden;
  position: absolute;
  top: 25%;
  width: 100%;
  color: white;
  opacity: 5;
}

#video-start {
  opacity: 5;
  animation: fadeOutText 5s 2s ease-out forwards;
}

#video-text p {
  margin-top: -24px;
  text-align: center;
  font-size: 48px;
  color: #ffffff;
}

@keyframes fadeOutText {
  100% {
    opacity: 0;
  }
}

.cd-number-wrapper {
width: 80px;
height: 189px;
top: 50%;
margin: 80px auto 0 auto;
font-size: 10em;
font-family: 'Londrina Outline'; /* Bowlby One SC */
}

/* .cd-number-five {
position: absolute;
opacity: 0;
margin: 0 auto 0 auto;
-webkit-animation: cd-number-five-anim 1.2s ease 0s 1 normal;
-moz-animation: cd-number-five-anim 1.2s ease 0s 1 normal;
-ms-animation: cd-number-five-anim 1.2s ease 0s 1 normal;
-o-animation: cd-number-five-anim 1.2s ease 0s 1 normal;
animation: cd-number-five-anim 1.2s ease 0s 1 normal;
}

@-webkit-keyframes cd-number-five-anim {
from {-webkit-transform: scale(0.5); opacity: 0;}
to {  -webkit-transform: scale(1.3); opacity: 1;}}

@-moz-keyframes cd-number-five-anim {
from {-moz-transform: scale(0.5); opacity: 0;}
to {  -moz-transform: scale(1.3); opacity: 1;}}

@-o-keyframes cd-number-five-anim {
from {-o-transform: scale(0.5); opacity: 0;}
to {  -o-transform: scale(1.3); opacity: 1;}}

@-ms-keyframes cd-number-five-anim {
from {-ms-transform: scale(0.5); opacity: 0;}
to {  -ms-transform: scale(1.3); opacity: 1;}}

@keyframes cd-number-five-anim {
from {transform: scale(0.5); opacity: 0;}
to {  transform: scale(1.3); opacity: 1;}}

.cd-number-four {
position: absolute;
opacity: 0;
-webkit-animation: cd-number-four-anim 1.2s ease 1.2s 1 normal;
-moz-animation: cd-number-four-anim 1.2s ease 1.2s 1 normal;
-ms-animation: cd-number-four-anim 1.2s ease 1.2s 1 normal;
-o-animation: cd-number-four-anim 1.2s ease 1.2s 1 normal;
animation: cd-number-four-anim 1.2s ease 1.2s 1 normal;
}

@-webkit-keyframes cd-number-four-anim {
from {-webkit-transform: scale(0.5); opacity: 0;}
to {  -webkit-transform: scale(1.3); opacity: 1;}}

@-moz-keyframes cd-number-four-anim {
from {-moz-transform: scale(0.5); opacity: 0;}
to {  -moz-transform: scale(1.3); opacity: 1;}}

@-o-keyframes cd-number-four-anim {
from {-o-transform: scale(0.5); opacity: 0;}
to {  -o-transform: scale(1.3); opacity: 1;}}

@-ms-keyframes cd-number-four-anim {
from {-ms-transform: scale(0.5); opacity: 0;}
to {  -ms-transform: scale(1.3); opacity: 1;}}

@keyframes cd-number-four-anim {
from {transform: scale(0.5); opacity: 0;}
to {  transform: scale(1.3); opacity: 1;}} */

.cd-number-three {
position: absolute;
opacity: 0;
-webkit-animation: cd-number-three-anim 1.2s ease 2.4s 1 normal;
-moz-animation: cd-number-three-anim 1.2s ease 2.4s 1 normal;
-ms-animation: cd-number-three-anim 1.2s ease 2.4s 1 normal;
-o-animation: cd-number-three-anim 1.2s ease 2.4s 1 normal;
animation: cd-number-three-anim 1.2s ease 2.4s 1 normal;
}

@-webkit-keyframes cd-number-three-anim {
from {-webkit-transform: scale(0.5); opacity: 0;}
to {  -webkit-transform: scale(1.3); opacity: 1;}}

@-moz-keyframes cd-number-three-anim {
from {-moz-transform: scale(0.5); opacity: 0;}
to {  -moz-transform: scale(1.3); opacity: 1;}}

@-o-keyframes cd-number-three-anim {
from {-o-transform: scale(0.5); opacity: 0;}
to {  -o-transform: scale(1.3); opacity: 1;}}

@-ms-keyframes cd-number-three-anim {
from {-ms-transform: scale(0.5); opacity: 0;}
to {  -ms-transform: scale(1.3); opacity: 1;}}

@keyframes cd-number-three-anim {
from {transform: scale(0.5); opacity: 0;}
to {  transform: scale(1.3); opacity: 1;}}

.cd-number-two {
position: absolute;
opacity: 0;
-webkit-animation: cd-number-two-anim 1.2s ease 3.6s 1 normal;
-moz-animation: cd-number-two-anim 1.2s ease 3.6s 1 normal;
-ms-animation: cd-number-two-anim 1.2s ease 3.6s 1 normal;
-o-animation: cd-number-two-anim 1.2s ease 3.6s 1 normal;
animation: cd-number-two-anim 1.2s ease 3.6s 1 normal;
}

@-webkit-keyframes cd-number-two-anim {
from {-webkit-transform: scale(0.5); opacity: 0;}
to {  -webkit-transform: scale(1.3); opacity: 1;}}

@-moz-keyframes cd-number-two-anim {
from {-moz-transform: scale(0.5); opacity: 0;}
to {  -moz-transform: scale(1.3); opacity: 1;}}

@-o-keyframes cd-number-two-anim {
from {-o-transform: scale(0.5); opacity: 0;}
to {  -o-transform: scale(1.3); opacity: 1;}}

@-ms-keyframes cd-number-two-anim {
from {-ms-transform: scale(0.5); opacity: 0;}
to {  -ms-transform: scale(1.3); opacity: 1;}}

@keyframes cd-number-two-anim {
from {transform: scale(0.5); opacity: 0;}
to {  transform: scale(1.3); opacity: 1;}}

.cd-number-one {
position: absolute;
opacity: 0;
-webkit-animation: cd-number-one-anim 1.2s ease 4.8s 1 normal;
-moz-animation: cd-number-one-anim 1.2s ease 4.8s 1 normal;
-ms-animation: cd-number-one-anim 1.2s ease 4.8s 1 normal;
-o-animation: cd-number-one-anim 1.2s ease 4.8s 1 normal;
animation: cd-number-one-anim 1.2s ease 4.8s 1 normal;
}

@-webkit-keyframes cd-number-one-anim {
from {-webkit-transform: scale(0.5); opacity: 0;}
to {  -webkit-transform: scale(1.3); opacity: 1;}}

@-moz-keyframes cd-number-one-anim {
from {-moz-transform: scale(0.5); opacity: 0;}
to {  -moz-transform: scale(1.3); opacity: 1;}}

@-o-keyframes cd-number-one-anim {
from {-o-transform: scale(0.5); opacity: 0;}
to {  -o-transform: scale(1.3); opacity: 1;}}

@-ms-keyframes cd-number-one-anim {
from {-ms-transform: scale(0.5); opacity: 0;}
to {  -ms-transform: scale(1.3); opacity: 1;}}

@keyframes cd-number-one-anim {
from {transform: scale(0.5); opacity: 0;}
to {  transform: scale(1.3); opacity: 1;}}

#modal-btn2{
  margin: 20px;
  margin-right: 12px;
  z-index: 1000;
  background-color: #5c6ac4;
  color: white;
  display: inline-block;
  font-weight: 400;
  line-height: 20px;
  text-align: center;
  text-decoration: none;
  vertical-align: middle;
  cursor: pointer;
  -webkit-user-select: none;
  -moz-user-select: none;
  user-select: none;
  border: 1px solid transparent;
  font-size: 20px;
  border-radius: 0.25rem;
  padding: 0;
  width: 200px;
  height: 60px;
  transition: color 0.15s ease-in-out, background-color 0.15s ease-in-out,
  border-color 0.15s ease-in-out, box-shadow 0.15s ease-in-out;
}
</style>
