<template>
  <div>
    <p>TTS</p>
    <!-- <vue-text-to-speech></vue-text-to-speech> -->
    <label for="select-lang">
      <select v-show="false" id="select-lang">
      </select>
      <textarea id="text" rows="5" cols="20"></textarea>
      <button id="btn-read">읽기</button>
    </label>
  </div>
</template>

<script>
export default {
  mounted() {
    function speak(text, optProp) {
      window.speechSynthesis.cancel(); // 현재 읽고있다면 초기화

      const prop = optProp || {};

      const speechMsg = new SpeechSynthesisUtterance();
      speechMsg.rate = prop.rate || 1; // 속도: 0.1 ~ 10
      speechMsg.pitch = prop.pitch || 1; // 음높이: 0 ~ 2
      speechMsg.lang = prop.lang || 'ko-KR';
      speechMsg.text = text;

      // SpeechSynthesisUtterance에 저장된 내용을 바탕으로 음성합성 실행
      window.speechSynthesis.speak(speechMsg);
    }

    // 이벤트 영역
    // const selectLang = document.getElementById('select-lang');
    // const text = document.getElementById('text');
    const btnRead = document.getElementById('btn-read');

    btnRead.addEventListener('click', (e) => {
      console.log(e);
      speak('안녕하세요', {
        rate: 0.9,
        pitch: 0.5,
        lang: 'ko-KR',
      });
    });
  },
};
</script>

<style>

</style>
