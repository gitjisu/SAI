<template>
  <div>
    <div class="max-w-xs relative space-y-3">
      <label
        for="search"
        class="text-gray-900"
      >
      <input
        type="text"
        id="search"
        v-model="searchTerm"
        placeholder="&#128269;  직업을검색하세요"
        class="p-3 mb-0.5 w-full form-control"
        style="width:460px; height:50px"
        @keydown.enter.prevent
      >
      </label>

      <ul
        v-if="searchCountries.length"
        class="border border-gray-300"
        style="width:460px;"
      >
        <li>
          <p id='result-count'>
            {{ countries.length }} 개의 직업중 {{ searchCountries.length }}개가 검색되었습니다.
          </p>
        </li>
        <li
            v-for="country in searchCountries"
            :key="country.name"
            @click="selectCountry(country.name)"
            @keydown="selectCountry(country.name)"
            @keyup="selectCountry(country.name)"
        >
          <p id='result' @click="addEnter(country.name)" @keydown="addEnter(country.name)">
            {{ country.name }}
          </p>
        </li>
      </ul>
      <div v-if="selectedCountry">
        <p v-for="(user, index) in Enters" :key="index" class='btn'
        id='selected-item'
        @click="selectedDeleteItem2(user)"
        @keydown="selectedDeleteItem2(user)">
        {{ user.name }}</p>
      </div>
    </div>
  </div>
</template>

<script>
import {
  ref,
  computed,
} from 'vue';
import { useStore } from 'vuex';
import countries from '@/data/countries.json';

export default {
  name: 'SearchBar',
  setup() {
    const store = useStore();
    let plusEnter = [];
    const isLoggedIn = computed(() => store.getters.isLoggedIn);
    const currentUser = computed(() => store.getters.currentUser);
    const Enters = computed(() => store.getters.userEnter);
    const searchTerm = ref('');

    const searchCountries = computed(() => {
      if (searchTerm.value === '') {
        return [];
      }

      let matches = 0;

      return countries.filter((country) => {
        if (country.name.toLowerCase().includes(searchTerm.value.toLowerCase()) && matches < 10) {
          matches += 1;
          return country;
        }
        return false;
      });
    });
    let selectedCountry = {};
    const selectedCountries = [];
    const selectCountry = (country) => {
      selectedCountry = {
        name: country,
      };
      selectedCountries.push(selectedCountry);
      searchTerm.value = '';
    };
    // 이름을 클릭하면
    const addEnter = function (event) {
      // { name : '직업명' } 으로 state의 plusjob에 저장
      const name = event;
      plusEnter.push({ name });
      store.dispatch('newEnter', {
        plusEnter,
      });
      plusEnter = [];
    };
    const deleteplus = function (event) {
      for (let i = 0; i < plusEnter.length; i += 1) {
        if (plusEnter[i].name === event) {
          plusEnter.splice(i, 1);
          i -= 1;
        }
        store.dispatch('newEnter', {
          plusEnter,
        });
        plusEnter = [];
      }
    };
    return {
      isLoggedIn,
      currentUser,
      countries,
      searchTerm,
      searchCountries,
      selectCountry,
      selectedCountry,
      selectedCountries,
      addEnter,
      deleteplus,
      Enters,
    };
  },
  methods: {
    selectedDeleteItem(event) {
      this.deleteItem = event;
      for (let i = 0; i < this.selectedCountries.length; i += 1) {
        if (this.selectedCountries[i].name === this.deleteItem) {
          this.selectedCountries.splice(i, 1);
          i -= 1;
        } this.$forceUpdate();
      }
    },
    selectedDeleteItem2(event) {
      const data = event.name;
      for (let i = 0; i < this.Enters.length; i += 1) {
        if (this.Enters[i].name === data) {
          this.Enters.splice(i, 1);
          i -= 1;
        } this.$forceUpdate();
      }
      const newEnters = JSON.parse(JSON.stringify(this.Enters));
      this.$store.dispatch('updateEnter', newEnters);
    },
  },
  // updated() {
  //   // 유저가 추가를 하면 즉 추가선택한 리스트의 길이가 0이 아니라면
  //   this.$nextTick(function () {
  //     if (this.selectedCountries.length !== 0) {
  //       for (let i = 0; i < this.selectedCountries.length; i += 1) {
  //         this.allEnterprises.push(this.selectedCountries[i].name);
  //       }
  //       const unique = {};
  //       this.allEnterprises.forEach((el) => {
  //         unique[el] = true;
  //       });
  //       for (let i = 0; i < this.userPickList.length; i += 1) {
  //         this.allEnterprises.push(this.userPickList[i]);
  //       }
  //       const unique3 = {};
  //       this.allEnterprises.forEach((el) => {
  //         unique3[el] = true;
  //       });
  //     }
  //     this.$emit('enterprises', this.allEnterprises);
  //   });
  // },
  // // 유저가 관심기업 업데이트 안했을때 자동 에밋
  created() {
    const originalEnters = JSON.parse(JSON.stringify(this.Enters));
    this.$store.dispatch('updateEnter', originalEnters);
  },
};
</script>

<style scoped>
#delete {
  font-size: 5px;
  vertical-align: top;
}
ul {
  z-index: 1000;
  position: absolute;
  overflow: scroll;
}
#selected-item{
  z-index: 1000;
  color: #5c6ac4;
  font-weight: 400;
  line-height: 1.5;
  text-align: center;
  text-decoration: none;
  vertical-align: middle;
  cursor: pointer;
  -webkit-user-select: none;
  -moz-user-select: none;
  user-select: none;
  font-size: 15px;
  border-radius: 0.25rem;
  padding: 0.5rem;
  transition: color 0.15s ease-in-out, background-color 0.15s ease-in-out,
  border-color 0.15s ease-in-out, box-shadow 0.15s ease-in-out;
  border: none;
}

@media (prefers-reduced-motion: reduce) {
  #selected-item {
    transition: none;
  }
}
#selected-item:hover {
  color: #5c6ac4;
  background-color: none;
  border: none;
}
ul {
  background-color: white;
  width: 300px;
  list-style:none;
  padding-left: 0;
}
#result-count {
  color: gray;
  font-size: 13px;
  text-align: center;
  margin-top: 10px;
}
li:hover {
 cursor: pointer;
 background-color: rgb(233, 233, 233);
}
#result {
  margin-bottom: 0;
  padding: 15px;
  text-align: center;
}
.form-control:focus {
  color: #000000;
  background-color: rgb(255, 255, 255);
  border-color: #ffffff;
  outline: 0;
  box-shadow: 0 0 0 0.1rem #5c6ac496;
}
</style>
