const hre = require("hardhat");

async function main() {
  const [deployer] = await hre.ethers.getSigners();
  const operator = process.env.LEDGER_OPERATOR_ADDRESS || deployer.address;

  const settlementVaultFactory = await hre.ethers.getContractFactory(
    "SettlementVault"
  );
  const settlementVault = await settlementVaultFactory.deploy(operator);

  await settlementVault.waitForDeployment();

  console.log("Deployer:", deployer.address);
  console.log("SettlementVault:", await settlementVault.getAddress());
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
