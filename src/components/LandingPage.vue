<template>
  <div class="landing-container">
    <h1>Welcome to MystiCanvas!</h1>
    <p>Embark on a journey of art and intuition. Can you guess the secret words that shaped these AI-generated abstract masterpieces?</p>
    <button @click="startGame">Start the Adventure</button>
  </div>
</template>

<script setup lang="ts">
const startGame = () => {
  console.log("Starting the game...");
  // Here you can add your logic to start the game or navigate to another component/page
};
</script>

<style>
.landing-container {
  max-width: 800px;
  margin: 40px auto;
  padding: 20px;
  text-align: center;
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
}

.landing-container h1 {
  margin: 0;
  color: #333;
}

.landing-container p {
  font-size: 18px;
  color: #555;
}

button {
  background-color: #007bff;
  color: white;
  border: none;
  padding: 10px 20px;
  font-size: 16px;
  cursor: pointer;
  border-radius: 5px;
  transition: background-color 0.3s ease;
}

button:hover {
  background-color: #0056b3;
}
</style>
